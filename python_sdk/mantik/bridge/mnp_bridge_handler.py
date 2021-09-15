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

import logging
import os
import tempfile
import typing as t
import contextlib
from time import sleep
import shutil

from google.protobuf.any_pb2 import Any
from mnp import Handler, PortConfiguration, SessionState, SessionHandler, AboutResponse
from mantik.util import zip_directory

from mantik.types import MantikHeader, Bundle, UnsupportedBridgeKindError
from mantik.bridge._stubs.mantik.bridge.bridge_pb2 import BridgeAboutResponse, MantikInitConfiguration
from . import kinds

BridgeProvider = t.Callable[[MantikHeader], kinds.Bridge]


class InvalidInputAmountError(Exception):
    """A session handler has an invalid amount of inputs."""


class InvalidOutputAmountError(Exception):
    """A session handler has an invalid amount of outputs."""


class MnpSessionHandler(SessionHandler, contextlib.AbstractContextManager):
    number_of_inputs: int = 0
    number_of_outputs: int = 0

    def __init__(self, session_id: str, header: MantikHeader, ports: PortConfiguration):
        self._check_for_correct_input_and_output(ports)
        super().__init__(session_id, ports=ports)
        self.header = header

    def __enter__(self) -> "MnpSessionHandler":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        pass

    def _check_for_correct_input_and_output(
        self,
        ports: PortConfiguration,
    ) -> None:
        if len(ports.inputs) != self.number_of_inputs:
            raise InvalidInputAmountError(
                f"{self.__class__.__name__} requires exactly "
                f"{self.number_of_inputs} input(s)"
            )
        elif len(ports.outputs) != self.number_of_outputs:
            raise InvalidOutputAmountError(
                f"{self.__class__.__name__} requires exactly "
                f"{self.number_of_outputs} output(s)"
            )


class MnpSessionHandlerForDataSet(MnpSessionHandler):
    number_of_inputs: int = 0
    number_of_outputs: int = 1

    def __init__(
        self, 
        session_id: str, 
        header: MantikHeader, 
        ports: PortConfiguration, 
        dataset: kinds.DataSet,
    ):
        super().__init__(session_id, header=header, ports=ports)
        self.dataset = dataset
    
    def run_task(self, task_id: str, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]) -> None:
        """Run the task."""
        result = self.dataset.get().__add__(self.header.type.output)
        data_output = result.encode(self.ports.outputs[0].content_type)
        outputs[0].write(data_output)
        outputs[0].close()

    def quit(self):
        self.dataset.close()


class MnpSessionHandlerForAlgorithm(MnpSessionHandler):
    number_of_inputs: int = 1
    number_of_outputs: int = 1

    def __init__(
        self, 
        session_id: str, 
        header: MantikHeader, 
        ports: PortConfiguration, 
        algorithm: kinds.Algorithm,
    ):
        super().__init__(session_id, header=header, ports=ports)
        self.algorithm = algorithm

    def quit(self):
        self.algorithm.close()
        shutil.rmtree(self.header.basedir)

    def run_task(self, task_id: str, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]):
        data_input = Bundle.decode(self.ports.inputs[0].content_type, inputs[0], self.header.type.input)
        result = self.algorithm.apply(data_input).__add__(self.header.type.output)
        data_output = result.encode(self.ports.outputs[0].content_type)
        outputs[0].write(data_output)
        outputs[0].close()


class MnpSessionHandlerForTrainableAlgorithm(MnpSessionHandlerForAlgorithm):
    number_of_inputs: int = 1
    number_of_outputs: int = 2

    def run_task(self, task_id: str, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]):
        logging.debug("Feeding data for training %s/%s", self.session_id, task_id)
        data_input = Bundle.decode(self.ports.inputs[0].content_type, inputs[0], self.header.training_type)
        logging.debug("Feeding data for training finished, starting training now")
        self.algorithm.train(data_input)
        # Note: some algorithms train async during migration to MNP
        while not self.algorithm.is_trained:
            logging.debug("Sleeping, training seems not yet finished. Async training should be avoid in the future")
            sleep(0.5)
        logging.debug("Training finished, serializing results")
        stats_output = self.algorithm.training_stats.__add__(self.header.stat_type)
        stats_encoded = stats_output.encode(self.ports.outputs[1].content_type)
        result_dir = self.algorithm.trained_data_dir
        # Sending zip response
        zip_directory(result_dir, outputs[0])
        outputs[0].close()
        # Sending stats
        outputs[1].write(stats_encoded)
        outputs[1].close()



BRIDGE_KIND_SESSION_HANDLERS: t.Dict[str, kinds.Bridge] = {
    "dataset": MnpSessionHandlerForDataSet,
    "algorithm": MnpSessionHandlerForAlgorithm,
    "trainable": MnpSessionHandlerForTrainableAlgorithm,
}


class MnpBridgeHandler(Handler):

    def __init__(
        self, 
        bridge_provider: BridgeProvider, 
        name: str,
        quit_handler: t.Optional[t.Callable[[], None]] = None,
        session_handlers: t.Optional[t.Dict[str, kinds.Bridge]] = None,
    ):
        self.bridge_provider = bridge_provider
        self.name = name
        self.session_handlers = session_handlers or BRIDGE_KIND_SESSION_HANDLERS.copy()
        self.quit_handler = quit_handler

    def about(self) -> AboutResponse:
        extra = Any()
        extra.Pack(BridgeAboutResponse())
        return AboutResponse(
            name=self.name,
            extra=extra
        )

    def quit(self):
        if self.quit_handler:
            self.quit_handler()

    def init_session(self, session_id: str, configuration: Any, ports: PortConfiguration,
                     callback: t.Callable[[SessionState], None] = None) -> SessionHandler:
        init_config = MantikInitConfiguration()
        if configuration.Is(MantikInitConfiguration.DESCRIPTOR):
            configuration.Unpack(init_config)
        else:
            raise ValueError("Expected Mantik init configuration")

        # TODO: Cleanup directory if init fails
        if callback:
            callback(SessionState.INITIALIZING)

        # TODO: Set SessionState DOWNLOADING when there is a Url present.
        header_file = self._prepare_directory(session_id, init_config)

        if callback:
            callback(SessionState.STARTING_UP)

        header = MantikHeader.load(header_file)

        kind = self.bridge_provider(header)

        try:
            session_handler = self.session_handlers[header.kind]
        except KeyError:
            raise UnsupportedBridgeKindError(
                f"{header.kind} not supported for bridge MantikHeader.kind"
            )
        else:
            return session_handler(session_id, header, ports, kind)

    def _prepare_directory(self, session_id: str, init_config: MantikInitConfiguration) -> str:
        """
        Prepares the temp directory for a bridge.
        Returns Path to the Mantik header in a temp directory, e.g.
        /tmp/fregljergljrgjl/MantikHeader
        """
        tempdir = tempfile.mkdtemp()
        logging.info("Initializing new Mantik Session %s in directory %s", session_id, tempdir)

        header_file = os.path.join(tempdir, "MantikHeader")
        with open(header_file, "w", encoding="utf-8") as f:
            f.write(init_config.header)

        payload = self._get_payload(init_config)
        if payload:
            MnpBridgeHandler._unpack_payload(payload, init_config.payload_content_type, tempdir)
            os.remove(payload)

        return header_file

    @staticmethod
    def _get_payload(init_config: MantikInitConfiguration) -> t.Optional[str]:
        """
        Download the embedded payload into a temporary file, returns None if there is one
        """
        if init_config.url:
            return MnpBridgeHandler._get_payload_url(init_config.url)
        elif init_config.content:
            return MnpBridgeHandler._get_payload_content(init_config.content)
        else:
            return None

    @staticmethod
    def _get_payload_url(url: str) -> str:
        import requests
        logging.info("Downloading %s", url)
        file = tempfile.mktemp()
        req = requests.get(url, timeout=60, stream=True)
        # TODO: More timeout checking here
        with open(file, "wb") as f:
            for chunk in req.iter_content(1024 * 1024):
                f.write(chunk)
        return file

    @staticmethod
    def _get_payload_content(content: bytes) -> str:
        file = tempfile.mktemp()
        with open(file, "wb") as f:
            f.write(content)
        return file

    @staticmethod
    def _unpack_payload(payload_file: str, content_type: str, temp_dir: str):
        destination = os.path.join(temp_dir, "payload")
        file_size = os.stat(payload_file).st_size
        if content_type == "application/zip":
            logging.info("Unzipping zip payload in %s of %d bytes to %s", payload_file, file_size, destination)
            import zipfile
            zip = zipfile.ZipFile(payload_file)
            zip.extractall(destination)
        else:
            logging.info("Placing pure file of %d bytes into %s", file_size, destination)
            os.rename(payload_file, destination)

