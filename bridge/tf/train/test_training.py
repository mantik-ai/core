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

from algorithm_wrapper import AlgorithmWrapper
from mantik.types import MantikHeader, Bundle
import os
import time


def test_simple_training():
    dir = "example/factor"
    mf = MantikHeader.load(os.path.join(dir, "MantikHeader"))

    with AlgorithmWrapper(mf) as wrapper:
        assert not wrapper.is_trained

        train_data = Bundle(value=[[1.0, 2.0], [2.0, 4.0]], type=mf.training_type)
        wrapper.train(train_data)

        wait_for(lambda: wrapper.is_trained)

        assert wrapper.is_trained

        stats = wrapper.training_stats
        assert len(stats.value) == 1
        assert stats.value[0][0][0] >= 1.9
        assert stats.value[0][0][0] <= 2.1

        assert wrapper.trained_data_dir == "my_export_dir"


def wait_for(f):
    timeout = time.time() + 10
    while time.time() < timeout:
        if f():
            return
        time.sleep(1)
    raise TimeoutError()
