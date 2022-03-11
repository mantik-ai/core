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
import logging
import typing as t
import time
import shutil

import mnp
import mantik.util
import mantik.types
import mantik.bridge.kinds as kinds

logger = logging.getLogger(__name__)

BridgeProvider = t.Callable[[mantik.types.MantikHeader], kinds.Bridge]


class InvalidInputAmountError(Exception):
    """A session handler has an invalid amount of inputs."""


class InvalidOutputAmountError(Exception):
    """A session handler has an invalid amount of outputs."""


class MnpSessionHandler(mnp.SessionHandler, contextlib.AbstractContextManager):
    number_of_inputs: int = 0
    number_of_outputs: int = 0

    def __init__(self, session_id: str, header: mantik.types.MantikHeader, ports: mnp.PortConfiguration):
        self._check_for_correct_input_and_output(ports)
        super().__init__(session_id=session_id, ports=ports)
        self.header = header

    def __enter__(self) -> "MnpSessionHandler":
        return self

    def __exit__(self, exc_type, exc_value, traceback) -> None:
        pass

    def run_task(self, task_id: str, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]) -> None:
        """Run a task for a certain bridge kind."""
        logger.debug(
            "%s is running task %s in session %s",
            self.__class__.__name__,
            task_id,
            self.session_id, 
        )
        self._run_task(inputs=inputs, outputs=outputs)

    @abc.abstractmethod
    def _run_task(self, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]) -> None:
        """Runs the respetive task for the handler and sends the result to the output."""

    def _check_for_correct_input_and_output(
        self,
        ports: mnp.PortConfiguration,
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
    number_of_outputs: int = 1

    def __init__(
        self, 
        session_id: str, 
        header: mantik.types.MantikHeader, 
        ports: mnp.PortConfiguration, 
        dataset: kinds.DataSet,
    ):
        super().__init__(session_id=session_id, header=header, ports=ports)
        self.dataset = dataset
    
    def _run_task(self, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]) -> None:
        data_output = self._get_dataset_output()
        _write_to_output_and_close(output=outputs[0], content=data_output)

    def _get_dataset_output(self) -> mantik.types.Bundle:
        logger.debug("Getting dataset %s", self.dataset)
        bundle = self.dataset.get()
        result = _encode_bundle(
            bundle=bundle,
            type=self.header.type.output,
            content_type=self.ports.outputs[0].content_type,
        )
        return result

    def quit(self):
        self.dataset.close()


class MnpSessionHandlerForAlgorithmBase(MnpSessionHandler):
    number_of_inputs: int = 1
    number_of_outputs: int = 1

    def __init__(
        self, 
        session_id: str, 
        header: mantik.types.MantikHeader, 
        ports: mnp.PortConfiguration, 
        algorithm: kinds.Algorithm,
    ):
        self._check_for_correct_input_and_output(ports)
        super().__init__(session_id=session_id, header=header, ports=ports)
        self.algorithm = algorithm

    def quit(self):
        self.algorithm.close()
        shutil.rmtree(self.header.basedir)

    def _prepare_data_from_input(self, input: t.BinaryIO) -> mantik.types.Bundle:
        return mantik.types.Bundle.decode(
            data=input, 
            content_type=self.ports.inputs[0].content_type, 
            assumed_type=self.header.type.input,
        )


class MnpSessionHandlerForAlgorithm(MnpSessionHandlerForAlgorithmBase):

    def _run_task(self, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]):
        result = self._apply_algorithm_on_inputs(inputs)
        _write_to_output_and_close(output=outputs[0], content=result)

    def _apply_algorithm_on_inputs(self, inputs: t.List[t.BinaryIO]) -> mantik.types.Bundle:
        data_input = self._prepare_data_from_input(input=inputs[0])
        result = self._apply_algorithm_on_data(data_input)
        return result

    
    def _apply_algorithm_on_data(self, data: mantik.types.Bundle) -> mantik.types.Bundle:
        bundle = self.algorithm.apply(data)
        encoded = _encode_bundle(
            bundle=bundle,
            type=self.header.type.output,
            content_type=self.ports.outputs[0].content_type,
        )
        return encoded


class MnpSessionHandlerForTrainableAlgorithm(MnpSessionHandlerForAlgorithmBase):
    number_of_outputs: int = 2

    def _run_task(self, inputs: t.List[t.BinaryIO], outputs: t.List[t.BinaryIO]):
        self._train_algorithm_with_inputs(inputs)
        self._write_train_result_to_outputs(outputs)

    def _train_algorithm_with_inputs(self, inputs: t.List[t.BinaryIO]) -> None:
        data_input = self._prepare_data_from_input(input=inputs[0])
        self._train_algorithm_with_data(data_input)
        self._wait_until_algorithm_is_trained()
    
    def _train_algorithm_with_data(self, data: mantik.types.Bundle) -> None:
        logger.debug("Feeding data for training finished, starting training now")
        self.algorithm.train(data)

    def _wait_until_algorithm_is_trained(self) -> None:
        # Note: some algorithms train async during migration to MNP
        while not self.algorithm.is_trained:
            logger.debug(
                "Sleeping, training seems not yet finished. "
                "Async training should be avoided in the future"
            )
            time.sleep(0.5)
        logger.debug("Training finished, serializing results")

    def _write_train_result_to_outputs(self, outputs: t.List[t.BinaryIO]) -> None:
        self._write_zipped_directory_to_output_and_close(output=outputs[0])
        stats_encoded = _encode_bundle(
            bundle=self.algorithm.training_stats,
            type=self.header.stat_type,
            content_type=self.ports.outputs[1].content_type,
        )
        _write_to_output_and_close(output=outputs[1], content=stats_encoded)

    def _write_zipped_directory_to_output_and_close(self, output: t.BinaryIO) -> None:
        logger.debug(
            "Sending zip response from %s to output %s", 
            self.algorithm.trained_data_dir, 
            output,
        )
        mantik.util.zip_directory(self.algorithm.trained_data_dir, writer=output)
        output.close()


def _encode_bundle(
    bundle: mantik.types.Bundle, 
    type: mantik.types.DataType, 
    content_type: str,
) -> mantik.types.Bundle:
    typed = bundle.set_type(type)
    encoded = typed.encode(content_type)
    return encoded


def _write_to_output_and_close(output: t.BinaryIO, content: t.Any) -> None:
    logger.debug("Sending content %s to output %s", content, output)
    output.write(content)
    output.close()
