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

import mantik.types
import numpy as np


def train(bundle: mantik.types.Bundle, meta: mantik.types.MetaVariables) -> mantik.types.Bundle:
    return mantik.types.Bundle()


def try_init():
    return


def apply(_, bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("x")
    data = np.array(coordinates)
    result = 2*data
    return mantik.types.Bundle.from_flat_column(result.tolist())
