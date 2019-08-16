from functools import wraps
import logging
import contextlib

from google.protobuf.empty_pb2 import Empty

from .compat import *

logger = logging.getLogger(__name__)


class Result:
    def __str__(self):
        return str(self.result)

    def compute(self) -> Bundle:
        execution_response = self._graph_executor_service.FetchDataSet(
            FetchItemRequest(
                session_id=self.session.session_id,
                dataset_id=self.result.item_id,
                encoding=ENCODING_JSON,
            )
        )
        self.bundle = execution_response.bundle
        return execution_response


class Client(object):
    def __init__(self, host, port):
        self.host = host
        self.port = port
        self._session = None

    # TODO (mq): use LocalProxy's everywhere
    @property
    def session(self):
        if self._session is None:
            raise RuntimeError("Outside of a session scope.")
        return self._session

    @property
    def version(self):
        response: VersionResponse = self._about_service.Version(Empty())
        return response.version

    def _add_algorithm(self, directory: str) -> str:
        response = self._debug_service.AddLocalMantikDirectory(
            AddLocalMantikDirectoryRequest(directory=directory)
        )
        logger.debug("Added item %s", response.name)
        return response.name

    def apply(self, algorithm: str, bundle):
        algo = self._graph_builder_service.Get(
            GetRequest(session_id=self.session.session_id, name=algorithm)
        )
        dataset = self._graph_builder_service.Literal(
            LiteralRequest(session_id=self.session.session_id, bundle=bundle)
        )
        logger.debug("Created Literal Node %s", dataset.item_id)
        result = self._graph_builder_service.AlgorithmApply(
            ApplyRequest(
                session_id=self.session.session_id,
                algorithm_id=algo.item_id,
                dataset_id=dataset.item_id,
            )
        )
        cache_result = self._graph_builder_service.Cached(
            CacheRequest(session_id=self.session.session_id, item_id=result.item_id)
        )
        promise = Result()
        promise.session = self.session
        promise.result = cache_result
        promise._graph_executor_service = self._graph_executor_service

        return promise

    @contextlib.contextmanager
    def enter_session(self):
        if self._session is not None:
            raise RuntimeError("Cannot stack sessions.")
        self._session = self._session_service.CreateSession(CreateSessionRequest())
        logger.debug("Created session %s", self.session.session_id)
        yield self
        self._session_service.CloseSession(
            CloseSessionRequest(session_id=self.session.session_id)
        )
        logger.debug("Closed session %s", self.session.session_id)

        self._session = None

    def __enter__(self):
        channel = grpc.insecure_channel(f"{self.host}:{self.port}")
        self._about_service = AboutServiceStub(channel)
        self._session_service = SessionServiceStub(channel)
        self._debug_service = DebugServiceStub(channel)
        self._graph_builder_service = GraphBuilderServiceStub(channel)
        self._graph_executor_service = GraphExecutorServiceStub(channel)

        self._connected = True
        logger.info("Connected to version %s", self.version)

        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._connected = False
