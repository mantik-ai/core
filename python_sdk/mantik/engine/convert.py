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

import functools

import mantik.types
from . import compat as stubs


@functools.singledispatch
def convert_bundle(bundle: mantik.types.Bundle) -> stubs.Bundle:
    """Convert mantik.types.Bundle to its protobuf equivalent."""
    return stubs.Bundle(
        data_type=stubs.DataType(json=bundle.type.to_json()),
        encoding=stubs.ENCODING_JSON,
        encoded=bundle.encode_json(),
    )


@convert_bundle.register
def _(bundle: stubs.Bundle) -> mantik.types.Bundle:
    return mantik.types.Bundle.decode_json(
        bundle.encoded,
        assumed_type=mantik.types.DataType.from_json(bundle.data_type.json),
    )