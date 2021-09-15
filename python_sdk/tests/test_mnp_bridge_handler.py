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

from google.protobuf.any_pb2 import Any
from mnp import PortConfiguration, InputPortConfiguration, OutputPortConfiguration

from mantik.types import Bundle, DataType
from mantik.bridge import Algorithm
from mantik.bridge._stubs.mantik.bridge.bridge_pb2 import MantikInitConfiguration
from mantik.bridge.mnp_bridge_handler import MnpBridgeHandler, MnpSessionHandlerForTrainableAlgorithm, \
    MnpSessionHandlerForAlgorithm
from mantik.types import MantikHeader
from mantik.types import MIME_MANTIK_BUNDLE
from typing import Optional, cast
import os
import io
import logging


class EchoAlgorithm(Algorithm):

    def __init__(self, header: MantikHeader):
        self.header = header
        self.is_closed = False

    def apply(self, bundle: Bundle) -> Bundle:
        return bundle

    def train(self, bundle: Bundle):
        pass

    @property
    def is_trained(self) -> bool:
        return True

    @property
    def training_stats(self) -> Bundle:
        raise NotImplementedError("Pure algorithm")

    @property
    def trained_data_dir(self) -> str:
        raise NotImplementedError("Pure algorithm")

    def close(self):
        self.is_closed = True


class SimpleTrainer(Algorithm):
    """
    Figures out the sum of training x values
    During application it multiples them
    """

    def __init__(self, header: MantikHeader):
        self.header = header
        self.is_closed = False
        self.training_dir = os.path.join(header.basedir, "payload")
        self.trained_file = os.path.join(self.training_dir, "sum")

    def train(self, bundle: Bundle):
        summarized = 0
        column = bundle.flat_column("x")
        for x in column:
            summarized = summarized + x
        os.mkdir(self.training_dir)
        logging.info("Trained sum %s", str(summarized))
        with open(self.trained_file, "w") as f:
            f.write(str(summarized))

    @property
    def is_trained(self) -> bool:
        return os.path.exists(self.trained_file)

    @property
    def training_stats(self) -> Bundle:
        f = self.read_factor()
        return Bundle.decode_json(str(f), DataType.from_json("\"bool\""))

    def read_factor(self):
        with open(self.trained_file, "r") as f:
            data = f.read()
            return int(data)

    def apply(self, bundle: Bundle) -> Bundle:
        assert self.is_trained
        factor = self.read_factor()
        column = bundle.flat_column("x")

        def apply(v):
            return factor * v

        result = list(map(apply, column))
        result_bundle = Bundle.from_flat_column(result)
        return result_bundle

    @property
    def trained_data_dir(self) -> str:
        return self.training_dir

    def close(self):
        self.is_closed = True


def test_mnp_bridge_simple_algorithm():
    """
    Test the MNP Bridge with a simple algorithm without payload
    """
    echo: Optional[EchoAlgorithm] = None

    def provider(header: MantikHeader) -> Algorithm:
        nonlocal echo
        echo = EchoAlgorithm(header)
        return echo

    quit_called = False

    def quit_handler():
        nonlocal quit_called
        quit_called = True

    handler = MnpBridgeHandler(provider, "echo", quit_handler)

    ports = PortConfiguration(
        inputs=[InputPortConfiguration("application/json")],
        outputs=[OutputPortConfiguration("application/json")]
    )

    header = """{"kind": "algorithm", "type":{"input":"float32", "output":"float32"}}"""

    init_config = MantikInitConfiguration(
        header=header
    )
    init_config_any = Any()
    init_config_any.Pack(init_config)

    session: MnpSessionHandlerForAlgorithm = cast(MnpSessionHandlerForAlgorithm,
                                                  handler.init_session("1234", init_config_any, ports))
    assert session.session_id == "1234"

    assert echo.header.has_training is False

    result = session.run_task_with_bytes("task1", [b"12.5"])
    assert result == [b"12.5"]

    assert os.path.exists(echo.header.basedir)

    session.quit()
    assert echo.is_closed

    assert not os.path.exists(echo.header.basedir)

    handler.quit()
    assert quit_called


def test_simple_training():
    """
    Test the MNP Handler with a simple training algorithm
    """
    echo: Optional[SimpleTrainer] = None

    def provider(header: MantikHeader) -> SimpleTrainer:
        nonlocal echo
        echo = SimpleTrainer(header)
        return echo

    quit_called = False

    def quit_handler():
        nonlocal quit_called
        quit_called = True

    handler = MnpBridgeHandler(provider, "multipler", quit_handler)

    ports = PortConfiguration(
        inputs=[InputPortConfiguration("application/json")],
        outputs=[OutputPortConfiguration("application/zip"), OutputPortConfiguration("application/json")]
    )

    header = """
    {
      "kind":"trainable",
      "trainingType":{
        "columns": {
          "x": "int32"
        }
      },
      "statsType":"int32",
      "type":{
        "input":{
          "columns":{
            "x": "int32"
          }
        },
        "output":{
          "columns":{
            "x": "int32"
          }
        }
      }
    }
    """

    init_config = MantikInitConfiguration(
        header=header
    )
    init_config_any = Any()
    init_config_any.Pack(init_config)

    session: MnpSessionHandlerForTrainableAlgorithm = cast(MnpSessionHandlerForTrainableAlgorithm,
                                                  handler.init_session("1234", init_config_any, ports))
    assert session.session_id == "1234"

    assert echo.header.has_training is True

    result = session.run_task_with_bytes("task1", [b"[[1],[2],[3]]"])
    assert result[0][:2] == b"PK"  # Magic bytes in ZIP
    assert result[1] == b"6"  # Stats result

    session.quit()
    assert echo.is_closed

    ports2 = PortConfiguration(
        inputs=[InputPortConfiguration("application/json")],
        outputs=[OutputPortConfiguration("application/json")]
    )

    execution_header = """
        {
            "kind":"algorithm",
            "type":{
                "input":{
                    "columns":{
                    "x": "int32"
                  }
                },
                "output":{
                  "columns":{
                    "x": "int32"
                  }
                }
            }
        }
        """
    init_config2 = MantikInitConfiguration(
        header=execution_header,
        payload_content_type="application/zip",
        content=result[0]
    )
    init_config2_any = Any()
    init_config2_any.Pack(init_config2)

    session2: MnpSessionHandlerForAlgorithm = cast(MnpSessionHandlerForAlgorithm,
                                                   handler.init_session("session2", init_config2_any, ports2))

    result2 = session2.run_task_with_bytes("task2", [b"[[1],[2],[3]]"])
    assert result2 == [b"[[6], [12], [18]]"]  # Multiplied by 6

    handler.quit()
    assert quit_called


def test_simple_training_with_msgpack():
    """
    Like the test_simple_training, but using msgpack with type encoding.
    """
    handler = MnpBridgeHandler(SimpleTrainer, "multipler")

    ports = PortConfiguration(
        inputs=[InputPortConfiguration(MIME_MANTIK_BUNDLE)],
        outputs=[OutputPortConfiguration("application/zip"), OutputPortConfiguration(MIME_MANTIK_BUNDLE)]
    )

    header = """
        {
          "kind":"trainable",
          "trainingType":{
            "columns": {
              "x": "int32"
            }
          },
          "statsType":"int32",
          "type":{
            "input":{
              "columns":{
                "x": "int32"
              }
            },
            "output":{
              "columns":{
                "x": "int32"
              }
            }
          }
        }
        """

    init_config = MantikInitConfiguration(
        header=header
    )
    init_config_any = Any()
    init_config_any.Pack(init_config)

    session: MnpSessionHandlerForTrainableAlgorithm = cast(MnpSessionHandlerForTrainableAlgorithm,
                                                  handler.init_session("1234", init_config_any, ports))
    assert session.session_id == "1234"

    header_parsed = MantikHeader.parse(header, ".")
    training = Bundle.decode_json("[[1],[2],[3]]", header_parsed.training_type)
    training_msgpack = training.encode_msgpack_bundle()

    result = session.run_task_with_bytes("task1", [training_msgpack])
    assert result[0][:2] == b"PK"  # Magic bytes in ZIP

    result_parsed = Bundle.decode_msgpack_bundle(io.BytesIO(result[1]))
    assert result_parsed.value == 6  # Stats result

    session.quit()

    ports2 = PortConfiguration(
        inputs=[InputPortConfiguration(MIME_MANTIK_BUNDLE)],
        outputs=[OutputPortConfiguration(MIME_MANTIK_BUNDLE)]
    )

    execution_header = """
            {
                "kind":"algorithm",
                "type":{
                    "input":{
                        "columns":{
                        "x": "int32"
                      }
                    },
                    "output":{
                      "columns":{
                        "x": "int32"
                      }
                    }
                }
            }
            """
    init_config2 = MantikInitConfiguration(
        header=execution_header,
        payload_content_type="application/zip",
        content=result[0]
    )
    init_config2_any = Any()
    init_config2_any.Pack(init_config2)

    session2: MnpSessionHandlerForAlgorithm = cast(MnpSessionHandlerForAlgorithm,
                                                   handler.init_session("session2", init_config2_any, ports2))

    input_data = Bundle.decode_json("[[1],[2],[3]]", header_parsed.type.input)
    input_data_msgpack = input_data.encode_msgpack_bundle()
    result2 = session2.run_task_with_bytes("task2", [input_data_msgpack])
    result_data_parsed = Bundle.decode_msgpack_bundle(io.BytesIO(result2[0]))
    assert result_data_parsed.flat_column("x") == [6, 12, 18]  # Multiplied by 6

    handler.quit()
