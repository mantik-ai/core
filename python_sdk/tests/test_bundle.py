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

import msgpack

import mantik.types


def test_json_serialisation():
    json = "[[1,2],[3,4]]"
    parsed = mantik.types.Bundle.decode_json(json)
    assert parsed.type is None
    assert parsed.value == [[1, 2], [3, 4]]

    parsed2 = mantik.types.Bundle.decode("application/json", io.StringIO(json))
    assert parsed2 == parsed

    assert mantik.types.Bundle.decode_json(parsed.encode_json()) == parsed
    assert mantik.types.Bundle.decode_json(parsed.encode("application/json")) == parsed


def test_json_bundle_serialisation():
    json = """
    {
        "type": { 
            "columns":{
                "a": "int32",
                "b": "string"
            }
        },
        "value": [[1,2],[3,4]]
    }     
    """
    parsed = mantik.types.Bundle.decode_json_bundle(json)
    assert parsed.type == mantik.types.DataType({"columns": {"a": "int32", "b": "string"}})
    assert parsed.value == [[1, 2], [3, 4]]

    serialized = parsed.encode_json_bundle()
    parsed_again = mantik.types.Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    parsed_again2 = mantik.types.Bundle.decode("application/x-mantik-bundle-json", io.StringIO(json))
    assert parsed_again2 == parsed
    assert mantik.types.Bundle.decode_json_bundle(parsed.encode("application/x-mantik-bundle-json")) == parsed


def test_json_bundle_serialisation_single():
    json = """
    {
        "type": "int32",
        "value": 1234
    }     
    """
    parsed = mantik.types.Bundle.decode_json_bundle(json)
    assert parsed.type == mantik.types.DataType("int32")
    assert parsed.value == 1234

    serialized = parsed.encode_json_bundle()
    parsed_again = mantik.types.Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    parsed_again2 = mantik.types.Bundle.decode("application/x-mantik-bundle-json", io.StringIO(json))
    assert parsed_again2 == parsed


def test_msgpack_serialisation():
    bundle = mantik.types.Bundle(None, [[1, 2], [3, 4]])
    packed = bundle.encode_msgpack()
    values = list(msgpack.Unpacker(io.BytesIO(packed)))
    assert values == [[1, 2], [3, 4]]

    unpacked = mantik.types.Bundle.decode_msgpack(io.BytesIO(packed))
    assert unpacked == bundle

    data_type = mantik.types.DataType({"columns": {"x": "int32"}})
    unpacked2 = mantik.types.Bundle.decode_msgpack(io.BytesIO(packed), data_type)
    assert unpacked2.value == bundle.value
    assert unpacked2.type == data_type
    assert mantik.types.Bundle.decode("application/x-msgpack", io.BytesIO(packed)) == bundle
    assert mantik.types.Bundle.decode_msgpack(io.BytesIO(bundle.encode("application/x-msgpack")))


def test_msgpack_bundle_serialisation():
    bundle = mantik.types.Bundle(mantik.types.DataType({"columns": {"x": "int32", "y": "string"}}), [[1, "Hello"], [2, "World"]])
    packed = bundle.encode_msgpack_bundle()

    elements = list(msgpack.Unpacker(io.BytesIO(packed), raw=False))
    expected = [{"format": {"columns": {"x": "int32", "y": "string"}}}, [1, "Hello"], [2, "World"]]
    assert elements == expected

    unpacked = bundle.decode_msgpack_bundle(io.BytesIO(packed))
    assert unpacked == bundle

    unpacked2 = bundle.decode("application/x-mantik-bundle", io.BytesIO(packed))
    assert unpacked2 == bundle

    unpacked3 = bundle.decode_msgpack_bundle(io.BytesIO(bundle.encode("application/x-mantik-bundle")))
    assert unpacked3 == bundle


def test_msgpack_bundle_single():
    bundle = mantik.types.Bundle(mantik.types.DataType("int32"), 1234)
    packed = bundle.encode_msgpack_bundle()
    unpacked = bundle.decode_msgpack_bundle(io.BytesIO(packed))
    assert unpacked == bundle

    unpacked2 = bundle.decode("application/x-mantik-bundle", io.BytesIO(packed))
    assert unpacked2 == bundle


def test_flat_column():
    bundle = mantik.types.Bundle(
        mantik.types.DataType(
            {
                "columns": {
                    "x": "int32",
                    "y": "string",
                    "z": {"type": "tensor", "shape": [2], "componentType": "float64"},
                }
            }
        ),
        [[1, "Hello", [1.5, 2.5]], [2, "World", [2.5, 3.5]]],
    )
    assert bundle.flat_column("x") == [1, 2]
    assert bundle.flat_column("y") == ["Hello", "World"]
    assert bundle.flat_column("z") == [[1.5, 2.5], [2.5, 3.5]]


def test_from_flat_column():
    got = mantik.types.Bundle.from_flat_column([[1.2, 2.4], [3.5, 4.5]])
    expected = mantik.types.Bundle(value=[[[1.2, 2.4]], [[3.5, 4.5]]])
    assert got == expected


def test_len():
    a = mantik.types.Bundle()
    assert len(a) == 1  # null
    b = mantik.types.Bundle(value=[[1], [2]])
    assert len(b) == 2
    c = mantik.types.Bundle(value="Hello World")
    assert len(c) == 1
    d = mantik.types.Bundle(value=4)
    assert len(d) == 1

def test_null_handling():
    json = """
    {
        "type": { 
            "columns":{
                "a": {
                    "type": "nullable",
                    "underlying": "int32"
                }
            }
        },
        "value": [[1], [null],[100]]
    }     
    """
    parsed = mantik.types.Bundle.decode_json_bundle(json)
    assert parsed.type == mantik.types.DataType({"columns": {"a": {"type": "nullable", "underlying": "int32"}}})
    assert parsed.value == [[1],[None],[100]]

    serialized = parsed.encode_json_bundle()
    parsed_again = mantik.types.Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    packed_msgpack = parsed.encode_msgpack_bundle()
    unpacked_msgpack = mantik.types.Bundle.decode_msgpack_bundle(io.BytesIO(packed_msgpack))
    assert unpacked_msgpack == parsed
