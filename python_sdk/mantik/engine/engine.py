from __future__ import annotations

import contextlib
import dataclasses
import functools
import logging
from typing import List, Optional, Union, Any

from google.protobuf.empty_pb2 import Empty

import mantik.types
from mantik.engine.compat import *

logger = logging.getLogger(__name__)

SomeData = Union[DataType, mantik.types.DataType]


@functools.singledispatch
def _convert(bundle: mantik.types.Bundle) -> Bundle:
    """Convert mantik.types.Bundle to its protobuf equivalent."""
    encoded = bundle.encode_json()
    print(encoded)
    return Bundle(
        data_type=DataType(json=bundle.type.to_json()),
        encoding=ENCODING_JSON,
        encoded=encoded,
    )


@_convert.register
def _(bundle: Bundle) -> mantik.types.Bundle:
    return mantik.types.Bundle.decode_json(
        bundle.encoded, assumed_type=mantik.types.DataType.from_json(bundle.data_type.json)
    )


@dataclasses.dataclass
class Dataset:

    dataset: Any
    _session: Any
    _graph_executor: Any

    @property
    def item_id(self):
        return self.dataset.item_id

    def __str__(self):
        return str(self.dataset)

    def fetch(self) -> Dataset:
        self.response = self._graph_executor.FetchDataSet(
            FetchItemRequest(
                session_id=self._session.session_id, dataset_id=self.item_id, encoding=ENCODING_JSON
            )
        )
        self.bundle = _convert(self.response.bundle)
        return self


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

    def upload_bundle(self, bundle: mantik.types.Bundle) -> NodeResponse:
        """Upload the bundle and create a mantik data literal."""
        # TODO (mq): cache this
        print(_convert(bundle))
        return self._graph_builder.Literal(
            LiteralRequest(session_id=self.session.session_id, bundle=_convert(bundle))
        )

    def make_pipeline(self, steps: List[str], data: Optional[SomeData] = None) -> NodeResponse:
        """Translate a list of references to a mantik Pipeline.

        A reference is either the name of an algorithm or a select literal.
        If the first pipeline step is a select literal, the input datatype must be supplied via a bundle.

        """

        def guess_input_type(data):
            if isinstance(data, mantik.types.Bundle):
                return DataType(json=data.type.to_json())
            # it should be a Literal
            return data.item.dataset.type

        def build_step(s):
            if s.startswith("select "):  # is a select literal
                # we need to supply the input data type
                return BuildPipelineStep(select=s)
            algorithm = self._graph_builder.Get(GetRequest(session_id=self.session.session_id, name=s))
            return BuildPipelineStep(algorithm_id=algorithm.item_id)

        pipe_steps = map(build_step, steps)

        request_args = dict(session_id=self.session.session_id, steps=pipe_steps)
        if steps[0].startswith("select "):  # pipe starts with select, need to supply input datatype
            request_args["input_type"] = guess_input_type(data)

        # TODO (mq): catch and convert exceptions to be pythonic
        pipe = self._graph_builder.BuildPipeline(BuildPipelineRequest(**request_args))
        return pipe

    def apply(self, pipe: Union[NodeResponse, List[str]], data: SomeData) -> Dataset:
        """Execute the pipeline pipe on some data."""
        dataset = self.upload_bundle(data) if isinstance(data, mantik.types.Bundle) else data
        mantik_pipe = pipe if isinstance(pipe, NodeResponse) else self.make_pipeline(pipe, data)
        logger.debug("Created Literal Node %s", dataset.item_id)
        result = self._graph_builder.AlgorithmApply(
            ApplyRequest(
                session_id=self.session.session_id, algorithm_id=mantik_pipe.item_id, dataset_id=dataset.item_id
            )
        )

        return Dataset(result, self._session, self._graph_executor)

    def train(self, pipe, data, no_caching=True):
        dataset = self.upload_bundle(data) if isinstance(data, mantik.types.Bundle) else data
        features = self.apply(pipe[:-1], dataset) if len(pipe) > 1 else dataset
        trainable = self._graph_builder.Get(GetRequest(session_id=self.session.session_id, name=pipe[-1]))
        trained, stats = self._graph_builder.Train(
            TrainRequest(
                session_id=self.session.session_id,
                trainable_id=trainable.item_id,
                training_dataset_id=features.item_id,
                no_caching=no_caching
            )
        )
        return trained, stats

    @contextlib.contextmanager
    def enter_session(self):
        if self._session is not None:
            raise RuntimeError("Cannot stack sessions.")
        self._session = self._session_service.CreateSession(CreateSessionRequest())
        logger.debug("Created session %s", self.session.session_id)
        yield self
        self._session_service.CloseSession(CloseSessionRequest(session_id=self.session.session_id))
        logger.debug("Closed session %s", self.session.session_id)

        self._session = None

    def __enter__(self):
        channel = grpc.insecure_channel(f"{self.host}:{self.port}")
        self._about_service = AboutServiceStub(channel)
        self._session_service = SessionServiceStub(channel)
        self._debug_service = DebugServiceStub(channel)
        self._graph_builder = GraphBuilderServiceStub(channel)
        self._graph_executor = GraphExecutorServiceStub(channel)

        self._connected = True
        logger.info("Connected to version %s", self.version)

        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._connected = False
