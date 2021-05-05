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

"""Computes y = 2*x on the sklearn stack via mantik."""

import logging

import mantik.engine
import mantik.types

logging.basicConfig(level=logging.INFO)

columns = mantik.types.DataType.from_kw(x="float64")
bundle = mantik.types.Bundle(columns, [[1.0], [2.0]])
pipe = ["multiply"]
pipe2 = ["select x * CAST(2 as float64) as y"]

with mantik.engine.Client("localhost", 8087) as client:
    # TODO (mq): We should be able to search/list all existing algorithms.
    client._add_algorithm("../../bridge/sklearn/simple_learn")
    client._add_algorithm("../../bridge/sklearn/simple_learn/example/multiply")
    with client.enter_session():
        result = client.apply(pipe, bundle).fetch()
        print(f"Result: {result.bundle}")
        # you can explicitly upload a bundle.
        # however, just passing the bundle works, too!
        data = client.upload_bundle(bundle)
        result2 = client.apply(pipe2, data).fetch()
        print(f"Result2: {result2.bundle}")

assert result.bundle == result2.bundle
