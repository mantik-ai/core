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
import json
import logging
import typing as t

import google.protobuf.empty_pb2 as protobuf

import mantik.types
import mantik.util
from . import compat as stubs
from . import convert
from . import objects

logger = logging.getLogger(__name__)


SomeData = t.Union[stubs.DataType, mantik.types.DataType, mantik.types.Bundle, objects.MantikObject]
PipeStep = t.Union[str, stubs.NodeResponse, objects.MantikObject]
Pipeline = t.Union[PipeStep, t.List[PipeStep]]


class Client:
    _about_service: stubs.AboutServiceStub
    _session_service: stubs.SessionServiceStub
    _debug_service: stubs.DebugServiceStub
    _graph_builder: stubs.GraphBuilderServiceStub
    _graph_executor: stubs.GraphExecutorServiceStub
    _local_registry_service: stubs.LocalRegistryService

    def __init__(self, host: str = "localhost", port: int = 8087):
        self.host = host
        self.port = port
        self._session = None
        self._address = f"{self.host}:{self.port}"

    def __enter__(self) -> "Client":
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
        response: stubs.VersionResponse = self._about_service.Version(protobuf.Empty())
        return response.version

    def add_artifact(self, directory: str, named_mantik_id: str = "") -> objects.MantikArtifact:
        request_iterator = _create_request_iterator(
            directory=directory,
            named_mantik_id=named_mantik_id,
        )
        response: stubs.AddArtifactResponse = self._local_registry_service.AddArtifact(
            request_iterator=request_iterator,
            target=self._address,  # credentials must be supplied explicitly
            channel_credentials=stubs.grpc.experimental.insecure_channel_credentials(),
        )
        artifact = objects.MantikArtifact(response)
        logger.debug("Added artifact %s", artifact)
        return artifact

    def upload_bundle(self, bundle: mantik.types.Bundle) -> objects.MantikItem:
        """Upload the bundle and create a mantik data literal."""
        # TODO (mq): cache this
        response: stubs.NodeResponse = self._graph_builder.Literal(
            stubs.LiteralRequest(
                session_id=self.session.session_id, bundle=convert.convert_bundle(bundle)
            )
        )
        item = objects.MantikItem(response)
        logger.debug("Created Literal Node %s", item)
        return item

    def get(self, dataset: objects.MantikArtifact, action_name: str = "") -> objects.MantikItem:
        """Evaluate a dataset and get the result as a MantikItem."""
        logger.debug("Evaluating dataset %s", dataset)
        item = self._make_item_from_artifact(dataset)
        result = self.fetch_item(item, action_name=action_name)
        return result
    
    def apply(
        self, pipeline: Pipeline, data: SomeData, action_name: str = None,
    ) -> objects.MantikItem:
        """Execute a pipeline on some data."""
        logger.debug("Applying pipeline %s on data %s", pipeline, data)
        dataset = self._create_dataset(data)
        mantik_pipeline = self._make_pipeline(steps=pipeline, data=data)
        item = self._apply_pipeline_on_dataset(pipeline=mantik_pipeline, dataset=dataset)
        fetched = self.fetch_item(item, action_name=action_name)
        return fetched
    
    def train(
        self,
        pipeline: Pipeline,
        data: SomeData,
        meta: dict = None,
        caching: bool = True,
        action_name: str = None,
    ) -> objects.MantikItem:
        """Transform a trainable pipeline to a pipeline.

        Only the last element must be a TrainableAlgorithm.


        Returns:
            - The trained algorithm
            - Statistics about the training

        """
        preprocessing_steps, trainable_step = pipeline[:-1], pipeline[-1]
        dataset = self._get_preprocessing_pipeline(
            pipeline=preprocessing_steps, data=data
        )
        trainable = self._get_trainable_as_item(trainable_step)
        trainable_id = self._set_meta_variables_and_get_new_trainable_id(
            trainable=trainable, meta=meta
        )
        trained_algorithm, stats = self._train_algorithm(
            trainable_id=trainable_id,
            dataset_id=dataset.item_id,
            caching=caching,
        )
        stats_fetched = self.fetch_item(stats, action_name=action_name)
        trained_pipeline = self._make_pipeline([*preprocessing_steps, trained_algorithm], dataset)
        return trained_pipeline, stats_fetched

    def tag(self, algo: objects.MantikItem, ref: str) -> objects.MantikItem:
        """Tag an algorithm algo with a reference ref."""
        response: stubs.NodeResponse = self._graph_builder.Tag(
            stubs.TagRequest(
                session_id=self.session.session_id,
                item_id=algo.item_id,
                named_mantik_id=ref,
            )
        )
        item = objects.MantikItem(response)
        self.save_item(item, name=ref)
        return  item

    def fetch_item(
        self,
        item: objects.MantikItem, 
        action_name: str = None,
    ) -> objects.MantikItem:
        """Fetch the bundle (value) of a MantikItem."""
        if item.is_fetched():
            return item
        meta = stubs.ActionMeta(name=action_name)
        response: stubs.FetchItemResponse = self._graph_executor.FetchDataSet(
            stubs.FetchItemRequest(
                session_id=self.session.session_id,
                dataset_id=item.item_id,
                encoding=stubs.ENCODING_JSON,
                meta=meta,
            ),
            timeout=30,
        )
        item.bundle = convert.convert_bundle(response.bundle)
        return item

    def save_item(self, item: objects.MantikItem, name: str = None) -> None:
        """Save a MantikItem."""
        save_args = dict(session_id=self.session.session_id, item_id=item.item_id)
        if name is not None:
            save_args["name"] = name
        self._graph_executor.SaveItem(
            stubs.SaveItemRequest(**save_args)
        )

    def deploy_item(self, item: objects.MantikItem, ingress_name: str) -> None:
        """Deploy a MantikItem."""
        self._graph_executor.DeployItem(
            stubs.DeployItemRequest(
                session_id=self.session.session_id,
                item_id=item.item_id,
                ingress_name=ingress_name,
            )
        )

    def _make_item_from_artifact(self, artifact: objects.MantikArtifact) -> objects.MantikItem:
        response: stubs.NodeResponse = self._graph_builder.Get(
            stubs.GetRequest(
                session_id=self.session.session_id,
                name=artifact.name,
            )
        )
        return objects.MantikItem(response)

    def _create_dataset(self, data: SomeData) -> objects.MantikItem:
        if isinstance(data, mantik.types.Bundle):
            return self.upload_bundle(data)
        elif isinstance(data, objects.MantikArtifact):
            return self._make_item_from_artifact(data)
        elif isinstance(data, objects.MantikItem):
            return data
        allowed_types = (str(arg) for arg in SomeData.__args__)
        raise ValueError(
            f"Data of type {type(data)} is not allowed. "
            f"Allowed types: {', '.join(allowed_types)}"
        )

    def _make_pipeline(
        self, steps: t.List[PipeStep], data: t.Optional[SomeData] = None
    ) -> objects.MantikItem:
        """Translate a list of references to a mantik Pipeline.

        A reference is either the name of an algorithm or a select literal.
        If the first pipeline step is a select literal, the input datatype must be supplied via a bundle.

        """
        if isinstance(steps, (objects.MantikItem, stubs.NodeResponse)):
            return steps
        
        request_args = self._create_build_pipeline_request_arguments(steps=steps, data=data)

        # TODO (mq): catch and convert exceptions to be pythonic
        response: stubs.NodeResponse = self._graph_builder.BuildPipeline(
            stubs.BuildPipelineRequest(**request_args)
        )
        return objects.MantikItem(response)

    def _create_build_pipeline_request_arguments(
        self, 
        steps: t.List[PipeStep],
        data: t.Optional[SomeData],
    ) -> dict:
        pipe_steps = map(self._build_pipeline_step, steps)
        request_args = dict(session_id=self.session.session_id, steps=pipe_steps)

        # if pipe starts with a select, need to supply input /datatype
        first = steps[0]
        if (isinstance(first, str) and first.startswith("select ")) or isinstance(
            first, stubs.SelectRequest
        ):
            request_args["input_type"] = _guess_input_type(data)
        return request_args

    def _build_pipeline_step(self, step: PipeStep) -> stubs.BuildPipelineStep:
        if isinstance(step, str):
            if step.startswith("select "):  # is a select literal
                return stubs.BuildPipelineStep(select=step)
            algorithm = self._graph_builder.Get(
                stubs.GetRequest(session_id=self.session.session_id, name=step)
            )
        elif isinstance(step, objects.MantikArtifact):
            algorithm = self._make_item_from_artifact(step)
        else:
            algorithm = step
        return stubs.BuildPipelineStep(algorithm_id=algorithm.item_id)

    def _apply_pipeline_on_dataset(self, pipeline: objects.MantikItem, dataset: objects.MantikItem) -> objects.MantikItem:
        logger.debug("Applying pipeline %s on dataset %s", pipeline, dataset)
        response: stubs.NodeResponse = self._graph_builder.AlgorithmApply(
            stubs.ApplyRequest(
                session_id=self.session.session_id,
                dataset_id=dataset.item_id,
                algorithm_id=pipeline.item_id,
            )
        )
        return objects.MantikItem(response)

    def _get_preprocessing_pipeline(self, pipeline: Pipeline, data: SomeData) -> objects.MantikItem:
        dataset = self._create_dataset(data)
        if pipeline:
            mantik_pipe = self._make_pipeline(steps=pipeline, data=dataset)
            dataset = self._apply_pipeline_on_dataset(pipeline=mantik_pipe, dataset=dataset)
        return dataset

    def _get_trainable_as_item(self, trainable: t.Union[str, object.MantikArtifact]) -> objects.MantikItem:
        name = trainable if isinstance(trainable, str) else trainable.name
        response: stubs.NodeResponse = self._graph_builder.Get(
            stubs.GetRequest(session_id=self.session.session_id, name=name)
        )
        return objects.MantikItem(response)

    def _set_meta_variables_and_get_new_trainable_id(
        self, 
        trainable: objects.MantikItem,
        meta: t.Optional[dict],
    ) -> str:
        if meta is not None:
            meta = {trainable.item_id: meta}
            meta_variables = self._set_meta(meta) 
            return meta_variables[trainable.item_id]
        return trainable.item_id

    def _train_algorithm(self, trainable_id: str, dataset_id: str, caching: bool) -> objects.MantikItem:
        train_response: stubs.TrainResponse = self._graph_builder.Train(
            stubs.TrainRequest(
                session_id=self.session.session_id,
                trainable_id=trainable_id,
                training_dataset_id=dataset_id,
                no_caching=not caching
            ),
            timeout=30,
        )
        trained_algorithm = objects.MantikItem(train_response.trained_algorithm)
        stats = objects.MantikItem(train_response.stat_dataset)
        return trained_algorithm, stats

    def _set_meta(self, meta: str = None) -> dict:
        """Set meta-variables and return a mapping from old algorithm ids to new algorithm ids."""
        meta = meta or {}

        def _set(item_id: str, variables: dict) -> str:
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


def _create_request_iterator(
    directory: str, named_mantik_id: str
) -> t.Iterator[stubs.AddArtifactRequest]:
    mantik_header = _read_mantik_header(directory)
    payload = _zip_payload(directory)
    request_iterator = _make_add_artifact_request_stream(
        mantik_header, named_mantik_id, payload
    )
    return request_iterator


def _read_mantik_header(directory: str) -> str:
    """Read mantik header file in given directory."""
    with open(directory + "/MantikHeader", "r") as f:
        return f.read()


def _zip_payload(directory: str) -> str:
    """Search for payload folder and return bitwise representation of zipped payload."""
    payload_dir = directory + "/payload"
    return mantik.util.get_zip_bit_representation(payload_dir) or ""


def _make_add_artifact_request_stream(
    mantik_header: str, named_mantik_id: str = "", payload: str = ""
) -> t.Iterator[stubs.AddArtifactRequest]:
    """Create stream of AddArtifactRequest."""
    content_type = "application/zip" if payload else None
    yield stubs.AddArtifactRequest(
        mantik_header=mantik_header,
        named_mantik_id=named_mantik_id,
        content_type=content_type,
        payload=payload,
    )


def _guess_input_type(data) -> stubs.DataType:
    if isinstance(data, mantik.types.Bundle):
        return stubs.DataType(json=data.type.to_json())
    # it should be a Literal
    return data.item.item.dataset.type
