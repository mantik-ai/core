from io import BytesIO
from io import StringIO

import msgpack

from mantik.types import DataType, Bundle


def test_json_serialisation():
    json = "[[1,2],[3,4]]"
    parsed = Bundle.decode_json(json)
    assert parsed.type is None
    assert parsed.value == [[1, 2], [3, 4]]

    parsed2 = Bundle.decode("application/json", StringIO(json))
    assert parsed2 == parsed

    assert Bundle.decode_json(parsed.encode_json()) == parsed
    assert Bundle.decode_json(parsed.encode("application/json")) == parsed


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
    parsed = Bundle.decode_json_bundle(json)
    assert parsed.type == DataType({"columns": {"a": "int32", "b": "string"}})
    assert parsed.value == [[1, 2], [3, 4]]

    serialized = parsed.encode_json_bundle()
    parsed_again = Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    parsed_again2 = Bundle.decode("application/x-mantik-bundle-json", StringIO(json))
    assert parsed_again2 == parsed
    assert Bundle.decode_json_bundle(parsed.encode("application/x-mantik-bundle-json")) == parsed


def test_json_bundle_serialisation_single():
    json = """
    {
        "type": "int32",
        "value": 1234
    }     
    """
    parsed = Bundle.decode_json_bundle(json)
    assert parsed.type == DataType("int32")
    assert parsed.value == 1234

    serialized = parsed.encode_json_bundle()
    parsed_again = Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    parsed_again2 = Bundle.decode("application/x-mantik-bundle-json", StringIO(json))
    assert parsed_again2 == parsed


def test_msgpack_serialisation():
    bundle = Bundle(None, [[1, 2], [3, 4]])
    packed = bundle.encode_msgpack()
    values = list(msgpack.Unpacker(BytesIO(packed)))
    assert values == [[1, 2], [3, 4]]

    unpacked = Bundle.decode_msgpack(BytesIO(packed))
    assert unpacked == bundle

    data_type = DataType({"columns": {"x": "int32"}})
    unpacked2 = Bundle.decode_msgpack(BytesIO(packed), data_type)
    assert unpacked2.value == bundle.value
    assert unpacked2.type == data_type
    assert Bundle.decode("application/x-msgpack", BytesIO(packed)) == bundle
    assert Bundle.decode_msgpack(BytesIO(bundle.encode("application/x-msgpack")))


def test_msgpack_bundle_serialisation():
    bundle = Bundle(DataType({"columns": {"x": "int32", "y": "string"}}), [[1, "Hello"], [2, "World"]])
    packed = bundle.encode_msgpack_bundle()

    elements = list(msgpack.Unpacker(BytesIO(packed), raw=False))
    expected = [{"format": {"columns": {"x": "int32", "y": "string"}}}, [1, "Hello"], [2, "World"]]
    assert elements == expected

    unpacked = bundle.decode_msgpack_bundle(BytesIO(packed))
    assert unpacked == bundle

    unpacked2 = bundle.decode("application/x-mantik-bundle", BytesIO(packed))
    assert unpacked2 == bundle

    unpacked3 = bundle.decode_msgpack_bundle(BytesIO(bundle.encode("application/x-mantik-bundle")))
    assert unpacked3 == bundle


def test_msgpack_bundle_single():
    bundle = Bundle(DataType("int32"), 1234)
    packed = bundle.encode_msgpack_bundle()
    unpacked = bundle.decode_msgpack_bundle(BytesIO(packed))
    assert unpacked == bundle

    unpacked2 = bundle.decode("application/x-mantik-bundle", BytesIO(packed))
    assert unpacked2 == bundle


def test_flat_column():
    bundle = Bundle(
        DataType(
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
    got = Bundle.from_flat_column([[1.2, 2.4], [3.5, 4.5]])
    expected = Bundle(value=[[[1.2, 2.4]], [[3.5, 4.5]]])
    assert got == expected


def test_len():
    a = Bundle()
    assert len(a) == 1  # null
    b = Bundle(value=[[1], [2]])
    assert len(b) == 2
    c = Bundle(value="Hello World")
    assert len(c) == 1
    d = Bundle(value=4)
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
    parsed = Bundle.decode_json_bundle(json)
    assert parsed.type == DataType({"columns": {"a": {"type": "nullable", "underlying": "int32"}}})
    assert parsed.value == [[1],[None],[100]]

    serialized = parsed.encode_json_bundle()
    parsed_again = Bundle.decode_json_bundle(serialized)
    assert parsed_again == parsed

    packed_msgpack = parsed.encode_msgpack_bundle()
    unpacked_msgpack = Bundle.decode_msgpack_bundle(BytesIO(packed_msgpack))
    assert unpacked_msgpack == parsed
