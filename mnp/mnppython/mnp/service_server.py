#
# This file is part of the Mantik Project.
# Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
# Authors: See AUTHORS file
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License version 3.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.
#
# Additionally, the following linking exception is granted:
#
# If you modify this Program, or any covered work, by linking or
# combining it with other code, such other code is not for that reason
# alone subject to any of the requirements of the GNU Affero GPL
# version 3.
#
# You can be released from the requirements of the license by purchasing
# a commercial license.
#

from concurrent import futures
from queue import Queue
from typing import Dict, Iterable, Optional

from mnp._stubs.mantik.mnp.mnp_pb2 import QuitResponse, InitResponse, SS_READY, SS_FAILED, PushResponse, PullResponse, \
    QueryTaskResponse, TS_UNKNOWN, TS_EXISTS, TS_FINISHED, TS_FAILED
from mnp._stubs.mantik.mnp.mnp_pb2 import AboutResponse as MnpAboutResponse
from mnp._stubs.mantik.mnp.mnp_pb2_grpc import MnpServiceServicer
from mnp.elements import SessionState as ESessionState, PortConfiguration
from mnp.handler import Handler, SessionHandler
from mnp.streammultiplexer import StreamMultiplexer
from mnp.forwarder import Forwarder
from threading import Lock
import logging


class SessionTask:
    """
    Handles a task on the server
    """

    def __init__(self, task_id: str, port_config: PortConfiguration):
        self.task_id = task_id
        self.port_config = port_config
        self.multiplexer = StreamMultiplexer(len(port_config.inputs), len(port_config.outputs))
        self.error_message = ""
        self.state = TS_EXISTS

    def start(self, executor: futures.Executor, session_handler: SessionHandler):
        """
        Start this task, it's running asynchronous
        """
        logging.info("Session %s Starting Task %s", session_handler.session_id, self.task_id)

        inputs = self.multiplexer.wrapped_inputs()
        outputs = self.multiplexer.wrapped_outputs()
        future = executor.submit(session_handler.run_task, self.task_id, inputs, outputs)
        self.start_portforwarders(executor)

        def on_done(future):
            try:
                future.result()
                logging.debug("Session %s Task %s done", session_handler.session_id, self.task_id)
                self.state = TS_FINISHED
            except Exception as e:
                msg = repr(e)
                logging.warning("Task %s failed, %s", self.task_id, msg, exc_info=True)
                self.error(repr(e))
            finally:
                self.multiplexer.close_output()

        future.add_done_callback(on_done)

    def start_portforwarders(self, executor: futures.Executor):
        started = 0
        for idx, output in enumerate(self.port_config.outputs):
            if output.forwarding is not None:
                forwarder = Forwarder(self.task_id, self.multiplexer.output_reader(idx), output.forwarding)
                executor.submit(forwarder.run)
                started += 1
        if started > 0:
            logging.info("Started %d forwarders", started)

    def error(self, error: str):
        """
        Set this task into an error state
        """
        logging.error("Task Error %s", error)
        self.error_message = error
        self.multiplexer.close()
        if self.state != TS_FINISHED:
            self.state = TS_FAILED
        pass

    def query(self) -> QueryTaskResponse:
        return QueryTaskResponse(
            state=self.state,
            error=self.error_message,
            inputs=self.multiplexer.input_port_status(),
            outputs=self.multiplexer.output_port_status()
        )


class SessionServer:
    """
    Handles a session on the server
    """

    tasks: Dict[str, SessionTask]

    def __init__(self, session_id: str, session_handler: SessionHandler, executor: futures.Executor):
        self.session_id = session_id
        self.handler = session_handler
        self.mutex = Lock()
        self.tasks = {}
        self.executor = executor

    def get_or_create_task(self, task_id: str) -> SessionTask:
        self.mutex.acquire()
        try:
            task = self.tasks.get(task_id)
            if task is None:
                task = SessionTask(task_id, self.handler.ports)
                self.tasks[task_id] = task
                task.start(self.executor, self.handler)
            return task
        except Exception as e:
            logging.error("Could not acquire task {}".format(task_id), exc_info=True)
            raise e
        finally:
            self.mutex.release()

    def get_task(self, task_id: str) -> Optional[SessionTask]:
        return self.tasks.get(task_id)

    def close(self):
        self.handler.quit()
        # No way yet to close running tasks, but we can stop their channels
        for task_id, task in self.tasks.items():
            task.error("Shutdown")


class ServiceServer(MnpServiceServicer):
    sessions: Dict[str, SessionServer]

    def __init__(self, handler: Handler, executor: futures.Executor):
        self.handler = handler
        self.sessions = {}
        self.executor = executor

    def About(self, request, context):
        response = self.handler.about()
        return response.as_proto()

    def Quit(self, request, context):
        self.handler.quit()
        for session_id, session in self.sessions.items():
            session.close()
        return QuitResponse()

    def Init(self, request, context):
        # Note: this methods returns an Iterable for the responses!

        ports = PortConfiguration.from_init(request)
        session_id = request.session_id
        configuration = request.configuration

        # Queue is not iterable, but we can convert to it, see https://stackoverflow.com/a/11288503
        sentinel = object()

        state_queue = Queue()

        def callback(state: ESessionState) -> None:
            logging.info("Session state %s", state)
            if state != ESessionState.READY and state != ESessionState.FAILED:
                state_queue.put(
                    InitResponse(
                        state=state.value
                    )
                )

        session_handler_future = self.executor.submit(
            self.handler.init_session,
            session_id,
            configuration,
            ports,
            callback
        )

        def done(f: futures.Future):
            try:
                session_handler = f.result()
                self.sessions[session_id] = SessionServer(session_id, session_handler, self.executor)
                logging.info("Initialized session %s", session_id)
                state_queue.put(
                    InitResponse(
                        state=SS_READY
                    )
                )
                state_queue.put(sentinel)
            except Exception as e:
                error_message = repr(e)
                logging.warning("Could not start session %s", session_id, exc_info=True)
                state_queue.put(
                    InitResponse(
                        state=SS_FAILED,
                        error=error_message
                    )
                )
                state_queue.put(sentinel)

        session_handler_future.add_done_callback(done)

        return iter(state_queue.get, sentinel)

    def Push(self, request_iterator, context):
        first = next(request_iterator)
        session = self.sessions.get(first.session_id)

        if session is None:
            context.set_details("Unknown session")
            raise ValueError("Unknown session")
        task_id = first.task_id
        port = first.port
        logging.debug("Pushing %s/%s/%d", first.session_id, task_id, port)
        task = session.get_or_create_task(task_id)

        task.multiplexer.push(port, first.data)
        done = first.done
        for n in request_iterator:
            task.multiplexer.push(port, n.data)
            done = n.done

        if done:
            task.multiplexer.push_eof(port)
        else:
            task.error("No EOF on port {}".format(port))

        return PushResponse()

    def Pull(self, request, context):
        session = self.sessions.get(request.session_id)
        if session is None:
            context.set_details("Unknown session")
            raise ValueError("Unknown session")

        task_id = request.task_id
        port = request.port
        logging.debug("Pulling %s/%s/%d", request.session_id, task_id, port)
        task = session.get_or_create_task(task_id)

        def respond() -> Iterable[PullResponse]:
            while True:
                data = task.multiplexer.pull(port)
                if not data: break
                yield PullResponse(data=data)
            yield PullResponse(done=True)

        return respond()

    def QuitSession(self, request, context):
        session = self.sessions.get(request.session_id)
        if session is None:
            context.set_details("Unknown session")
            raise ValueError("Unknown session")
        session.close()
        self.sessions.pop(request.session_id, None)
        return QuitResponse()

    def QueryTask(self, request, context):
        session = self.sessions.get(request.session_id)
        if session is None:
            context.set_details("Unknown session")
            raise ValueError("Unknown session")
        task_id = request.task_id
        ensure: bool = request.ensure
        task: SessionTask
        if ensure:
            task = session.get_or_create_task(task_id)
        else:
            task = session.get_task(task_id)

        if task is None:
            return QueryTaskResponse(state=TS_UNKNOWN)

        return task.query()
