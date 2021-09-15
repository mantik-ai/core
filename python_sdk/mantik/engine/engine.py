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

from __future__ import annotations

import abc
import contextlib
import dataclasses
import functools
import json
import logging
import typing as t

from google.protobuf.empty_pb2 import Empty

import mantik.types
from . import compat as stubs

from mantik.util import get_zip_bit_representation

logger = logging.getLogger(__name__)


class InvalidInputDataError(Exception):
    """Given input data are not supported to apply a pipeline."""


class MantikObject(abc.ABC):
    """Base class for an object in the registry/graph."""

    @property
    @abc.abstractmethod
    def item_id(self) -> str:
        """Return the ID of the item."""


@dataclasses.dataclass
class MantikArtifact(MantikObject):
    """An artifact from the mantik registry."""
    item: stubs.AddArtifactResponse

    @classmethod
    def from_add_artifact_response(
        cls, response: stubs.AddArtifactResponse
    ) -> MantikArtifact:
        return cls(item=response)

    @property
    def item_id(self) -> str:
        return self.item.artifact.item_id

    @property
    def name(self) -> str:
        return self.item_id


@dataclasses.dataclass
class MantikItem(MantikObject):
    """An item in a computational graph."""

    item: stubs.NodeResponse
    _session: stubs.CreateSessionResponse
    _graph_executor: stubs.GraphExecutorServiceStub
    bundle: mantik.types.Bundle = None
    _f_response: stubs.FetchItemResponse = None
    _s_response: stubs.SaveItemResponse = None
    _d_response: stubs.DeployItemResponse = None

    def __str__(self):
        return str(self.item)

    @classmethod
    def from_artifact(
        cls, 
        item: MantikArtifact,
        session: stubs.SessionServiceStub,
        graph_executor: stubs.GraphExecutorServiceStub,
        graph_builder: stubs.GraphBuilderServiceStub,
    ) -> MantikItem:
        response: stubs.NodeResponse = graph_builder.Get(
            stubs.GetRequest(
                session_id=session.session_id,
                name=item.name,
            )
        )
        return cls.from_node_response(
            response=response,
            session=session,
            graph_executor=graph_executor,
        )

    @classmethod
    def from_node_response(
        cls, 
        response: stubs.NodeResponse,
        session: stubs.SessionServiceStub,
        graph_executor: stubs.GraphExecutorServiceStub,
    ) -> MantikItem:
        return cls(
            item=response,
            _session=session,
            _graph_executor=graph_executor,
        )

    @property
    def item_id(self) -> str:
        return self.item.item_id

    def fetch(self, action_name: str = None) -> MantikItem:
        if self._f_response is None:
            meta = stubs.ActionMeta(name=action_name)
            self._f_response: stubs.FetchItemResponse = self._graph_executor.FetchDataSet(
                stubs.FetchItemRequest(
                    session_id=self._session.session_id,
                    dataset_id=self.item_id,
                    encoding=stubs.ENCODING_JSON,
                    meta=meta,
                ),
                timeout=30,
            )
            self.bundle = _convert(self._f_response.bundle)
        return self

    def save(self, name=None) -> MantikItem:
        if self._s_response is None:
            save_args = dict(session_id=self._session.session_id, item_id=self.item_id)
            if name is not None:
                save_args["name"] = name
            self._s_reponse: stubs.SaveItemResponse = self._graph_executor.SaveItem(
                stubs.SaveItemRequest(**save_args)
            )
        return self

    def deploy(self, ingress_name=str) -> MantikItem:
        if self._d_response is None:
            self._d_response: stubs.DeployItemResponse = self._graph_executor.DeployItem(
                stubs.DeployItemRequest(
                    session_id=self._session.session_id,
                    item_id=self.item_id,
                    ingress_name=ingress_name,
                )
            )
        return self


@functools.singledispatch
def _convert(bundle: mantik.types.Bundle) -> stubs.Bundle:
    """Convert mantik.types.Bundle to its protobuf equivalent."""
    return stubs.Bundle(
        data_type=stubs.DataType(json=bundle.type.to_json()),
        encoding=stubs.ENCODING_JSON,
        encoded=bundle.encode_json(),
    )


@_convert.register
def _(bundle: stubs.Bundle) -> mantik.types.Bundle:
    return mantik.types.Bundle.decode_json(
        bundle.encoded,
        assumed_type=mantik.types.DataType.from_json(bundle.data_type.json),
    )


SomeData = t.Union[stubs.DataType, mantik.types.DataType, mantik.types.Bundle, MantikObject]
PipeStep = t.Union[str, MantikObject, stubs.NodeResponse]
Pipeline = t.Union[PipeStep, t.List[PipeStep]]


class Client:
    _about_service: stubs.AboutServiceStub
    _session_service: stubs.SessionServiceStub
    _debug_service: stubs.DebugServiceStub
    _graph_builder: stubs.GraphBuilderServiceStub
    _graph_executor: stubs.GraphExecutorServiceStub
    _local_registry_service: stubs.LocalRegistryService

    def __init__(self, host, port):
        self.host = host
        self.port = port
        self._session = None
        self._address = f"{self.host}:{self.port}"

    def __enter__(self):
        channel = stubs.grpc.insecure_channel(self._address)
        self._about_service = stubs.AboutServiceStub(channel)
        self._session_service = stubs.SessionServiceStub(channel)
        self._debug_service = stubs.DebugServiceStub(channel)
        self._graph_builder = stubs.GraphBuilderServiceStub(channel)
        self._graph_executor = stubs.GraphExecutorServiceStub(channel)
        self._local_registry_service = stubs.LocalRegistryService()

        self._connected = True
        logger.info("Connected to version %s", self.version)

        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._connected = False

    @contextlib.contextmanager
    def enter_session(self):
        if self._session is not None:
            raise RuntimeError("Cannot stack sessions.")
        self._session = self._session_service.CreateSession(stubs.CreateSessionRequest())
        logger.debug("Created session %s", self.session.session_id)
        yield self
        self._session_service.CloseSession(
            stubs.CloseSessionRequest(session_id=self.session.session_id)
        )
        logger.debug("Closed session %s", self.session.session_id)

        self._session = None

    # TODO (mq): use LocalProxy's everywhere
    @property
    def session(self):
        if self._session is None:
            raise RuntimeError("Outside of a session scope.")
        return self._session

    @property
    def version(self):
        response: stubs.VersionResponse = self._about_service.Version(Empty())
        return response.version

    def add_artifact(self, directory: str, named_mantik_id: str = "") -> MantikArtifact:
        mantik_header = self._read_mantik_header(directory)
        payload = self._zip_payload(directory)
        request_iterator = self._make_add_artifact_request_stream(
            mantik_header, named_mantik_id, payload
        )
        response: stubs.AddArtifactResponse = self._local_registry_service.AddArtifact(
            request_iterator=request_iterator,
            target=self._address,  # credentials must be supplied explicitly
            channel_credentials=stubs.grpc.experimental.insecure_channel_credentials(),
        )
        logger.debug(
            "Added artifact %s", response.artifact.item_id
        )
        return self._make_artifact_from_add_artifact_response(response)

    def upload_bundle(self, bundle: mantik.types.Bundle) -> MantikItem:
        """Upload the bundle and create a mantik data literal."""
        # TODO (mq): cache this
        response: stubs.NodeResponse = self._graph_builder.Literal(
            stubs.LiteralRequest(
                session_id=self.session.session_id, bundle=_convert(bundle)
            )
        )
        item = self._make_item_from_node_response(response)
        logger.debug("Created Literal Node %s", item.item_id)
        return item

    def get(self, dataset: MantikArtifact, action_name: str = "") -> MantikItem:
        """Evaluate a dataset and get the result as a MantikItem."""
        logger.debug("Evaluating dataset %s", dataset)
        item = self._make_item_from_artifact(dataset)
        result = item.fetch(action_name=action_name)
        return result
    
    def apply(
        self, pipe: Pipeline, data: SomeData, action_name: str = None,
    ) -> MantikItem:
        """Execute the pipeline pipe on some data."""
        dataset = self._create_dataset(data)
        mantik_pipe = (
            pipe
            if isinstance(pipe, (MantikItem, stubs.NodeResponse))
            else self._make_pipeline(pipe, data)
        )
        result: stubs.NodeResponse = self._graph_builder.AlgorithmApply(
            stubs.ApplyRequest(
                session_id=self.session.session_id,
                dataset_id=dataset.item_id,
                algorithm_id=mantik_pipe.item_id,
            )
        )
        item = self._make_item_from_node_response(result)
        fetched = item.fetch(action_name=action_name)
        return fetched

    def train(
        self,
        pipe: Pipeline,
        data: SomeData,
        meta: dict = None,
        caching: bool = True,
        action_name: str = None,
    ) -> MantikItem:
        """Transform a trainable pipeline to a pipeline.

        Only the last element must be a TrainableAlgorithm.


        Returns:
            - The trained algorithm
            - Statistics about the training
        """
        dataset = self._create_dataset(data)
        features = (
            dataset
            if len(pipe) == 1
            else self.apply(pipe[:-1], dataset)
        )
        last = pipe[-1]
        name = last if isinstance(last, str) else last.name
        trainable: stubs.NodeResponse = self._graph_builder.Get(
            stubs.GetRequest(session_id=self.session.session_id, name=name)
        )
        meta = {trainable.item_id: meta} if meta is not None else meta
        new_ids = self._set_meta(meta)
        old_id = trainable.item_id
        train_response: stubs.TrainResponse = self._graph_builder.Train(
            stubs.TrainRequest(
                session_id=self.session.session_id,
                trainable_id=new_ids.get(old_id, old_id),
                training_dataset_id=features.item_id,
                no_caching=not caching
            ),
            timeout=30,
        )
        trained_algorithm = self._make_item_from_node_response(train_response.trained_algorithm)
        stats = self._make_item_from_node_response(train_response.stat_dataset)
        stats_fetched = stats.fetch(action_name=action_name)
        pipeline = self._make_pipeline([*pipe[:-1], trained_algorithm], data)
        return pipeline, stats_fetched

    def tag(self, algo: MantikItem, ref: str) -> MantikItem:
        """Tag an algorithm algo with a reference ref."""
        response: stubs.NodeResponse = self._graph_builder.Tag(
            stubs.TagRequest(
                session_id=self.session.session_id,
                item_id=algo.item_id,
                named_mantik_id=ref,
            )
        )
        return self._make_item_from_node_response(response).save(name=ref)

    def _make_artifact_from_add_artifact_response(self, response: stubs.AddArtifactResponse) -> MantikArtifact:
        """Return an artifact."""
        return MantikArtifact.from_add_artifact_response(response)

    def _make_item_from_node_response(self, response: stubs.NodeResponse) -> MantikItem:
        """Return a fetchable dataset."""
        return MantikItem.from_node_response(
            response=response, 
            session=self._session, 
            graph_executor=self._graph_executor,
        )

    def _make_item_from_artifact(self, response: MantikArtifact) -> MantikItem:
        return MantikItem.from_artifact(
            item=response,
            session=self.session,
            graph_builder=self._graph_builder,
            graph_executor=self._graph_executor,
        )

    def _create_dataset(self, data: SomeData) -> MantikItem:
        if isinstance(data, mantik.types.Bundle):
            return self.upload_bundle(data)
        elif isinstance(data, MantikArtifact):
            return self.get(data)
        elif isinstance(data, MantikItem):
            return data
        raise InvalidInputDataError(
            f"Data of type {type(data)} is not allowed. ",
            f"Allowed types: {mantik.types.Bundle.__name__}, "
            f"{MantikArtifact.__name__}, {MantikItem.__name__}"
        )

    def _make_pipeline(
        self, steps: t.List[PipeStep], data: t.Optional[SomeData] = None
    ) -> MantikItem:
        """Translate a list of references to a mantik Pipeline.

        A reference is either the name of an algorithm or a select literal.
        If the first pipeline step is a select literal, the input datatype must be supplied via a bundle.

        """

        def guess_input_type(data):
            if isinstance(data, mantik.types.Bundle):
                return stubs.DataType(json=data.type.to_json())
            # it should be a Literal
            return data.item.item.dataset.type

        def build_step(s):
            if isinstance(s, str):
                if s.startswith("select "):  # is a select literal
                    return stubs.BuildPipelineStep(select=s)
                algorithm = self._graph_builder.Get(
                    stubs.GetRequest(session_id=self.session.session_id, name=s)
                )
            elif isinstance(s, MantikArtifact):
                algorithm = self._make_item_from_artifact(s)
            else:
                algorithm = s
            return stubs.BuildPipelineStep(algorithm_id=algorithm.item_id)

        pipe_steps = map(build_step, steps)
        request_args = dict(session_id=self.session.session_id, steps=pipe_steps)

        # if pipe starts with a select, need to supply input /datatype
        first = steps[0]
        if (isinstance(first, str) and first.startswith("select ")) or isinstance(
            first, stubs.SelectRequest
        ):
            request_args["input_type"] = guess_input_type(data)

        # TODO (mq): catch and convert exceptions to be pythonic
        pipe: stubs.NodeResponse = self._graph_builder.BuildPipeline(stubs.BuildPipelineRequest(**request_args))
        return self._make_item_from_node_response(pipe)

    def _read_mantik_header(self, directory: str) -> str:
        """Read mantik header file in given directory."""
        with open(directory + "/MantikHeader", "r") as f:
            return f.read()

    def _zip_payload(self, directory: str) -> str:
        """Search for payload folder and return bitwise representation of zipped payload."""
        payload_dir = directory + "/payload"
        return get_zip_bit_representation(payload_dir) or ""

    def _make_add_artifact_request_stream(
        self, mantik_header: str, named_mantik_id: str = "", payload: str = ""
    ) -> t.Iterator[stubs.AddArtifactRequest]:
        """Create stream of AddArtifactRequest."""
        content_type = "application/zip" if payload else None
        yield stubs.AddArtifactRequest(
            mantik_header=mantik_header,
            named_mantik_id=named_mantik_id,
            content_type=content_type,
            payload=payload,
        )

    def _set_meta(self, meta=None) -> dict:
        """Set meta-variables and return a mapping from old algorithm ids to new algorithm ids."""
        meta = {} if meta is None else meta

        def _set(item_id, variables):
            return self._graph_builder.SetMetaVariables(
                stubs.SetMetaVariableRequest(
                    session_id=self.session.session_id,
                    item_id=item_id,
                    values=[
                        stubs.MetaVariableValue(name=name, json=json.dumps(value))
                        for name, value in variables.items()
                    ],
                )
            ).item_id

        return {
            item_id: _set(item_id, variables) for item_id, variables in meta.items()
        }
