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

import io
from dataclasses import dataclass
from dataclasses import field
from typing import Optional

import pytest

from mantik import types
from mantik.bridge import mnp_bridge_handler as handler
from mantik.bridge.kinds import DataSet
import mnp


class FakeDataSet(DataSet):
    def get(self) -> types.Bundle:
        return types.Bundle()

    
@dataclass
class FakeDataSetMantikHeader(types.MantikHeader):
    yaml_code: dict = field(default_factory=dict)
    basedir: str = "test_base_dir"
    name: str = "test_mantik_header"
    type: types.FunctionType = types.DataSetFunctionType(
        output=types.DataType("test_output"),
    )
    meta_variables: types.MetaVariables = field(default_factory=types.MetaVariables)
    kind: str = "dataset"
    training_type: Optional[types.DataType] = None
    stat_type: Optional[types.DataType] = None


@pytest.fixture()
def handler_for_dataset():
    header = FakeDataSetMantikHeader()
    dataset = FakeDataSet()
    output_ports = mnp.OutputPortConfiguration(
        "test",
        forwarding="test",
    )
    ports = mnp.PortConfiguration(outputs=[output_ports])
    return handler.MnpSessionHandlerForDataSet(
        "test_session_id",
        header=header,
        ports=ports,
        dataset=dataset,
    )


class TestMnpSessionHandlerForDataSet:
    def test_run_task(self, handler_for_dataset):
        inputs = []
        outputs = [io.BytesIO()]
        expected = types.Bundle()

        handler_for_dataset.run_task("test_task_id", inputs, outputs)
        [result] = outputs

        assert isinstance(result, io.BytesIO)
