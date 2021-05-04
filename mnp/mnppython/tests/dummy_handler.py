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

from io import RawIOBase
from typing import Callable, List

from google.protobuf.any_pb2 import Any

from mnp import Handler, SessionHandler, SessionState
from mnp.handler import PortConfiguration, AboutResponse


class DummySession(SessionHandler):

    def __init__(self, session_id: str, ports: PortConfiguration):
        super().__init__(session_id, ports)
        self.quit_requested = False

    def quit(self):
        self.quit_requested = True

    def run_task(self, task_id: str, inputs: List[RawIOBase], outputs: List[RawIOBase]):
        assert len(inputs) == len(outputs)
        for i in range(len(inputs)):
            input_data = inputs[i].read()
            outputs[i].write(input_data)
            outputs[i].close()


class DummyHandler(Handler):
    def __init__(self):
        self.quit_called = False
        self.sessions = []

    def about(self) -> AboutResponse:
        return AboutResponse("Dummy Handler")

    def quit(self):
        self.quit_called = True
        return

    def get_session(self, idx: int) -> DummySession:
        return self.sessions[idx]

    def init_session(self, session_id: str, configuration: Any, ports: PortConfiguration,
                     callback: Callable[[SessionState], None] = None) -> SessionHandler:
        callback(SessionState.INITIALIZING)
        if len(ports.inputs) != len(ports.outputs):
            raise ValueError("Port count must match for dummy handler")
        session = DummySession(session_id, ports)
        callback(SessionState.DOWNLOADING)
        self.sessions.append(session)
        return session
