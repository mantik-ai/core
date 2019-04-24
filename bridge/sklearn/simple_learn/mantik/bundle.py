from .data_type import DataType

import json
import msgpack

# Mime constants
MIME_JSON = "application/json"
MIME_MANTIK_JSON_BUNDLE = "application/x-mantik-bundle-json"
MIME_MSGPACK = "application/x-msgpack"
MIME_MANTIK_BUNDLE = "application/x-mantik-bundle"

# All Mime types, preferred first.
MIME_TYPES = [MIME_JSON, MIME_MANTIK_JSON_BUNDLE, MIME_MANTIK_BUNDLE, MIME_MSGPACK]


class Bundle(object):
    """
    A Mantik Bundle (Datatype and Value)
    """

    def __init__(self, data_type: DataType = None, value=None):
        """
        Init a Bundle with type and value
        """
        self.type = data_type
        self.value = value

    def flat_column(self, column_name: str):
        """
        Select a single column and returns a flat list of each value.
        Only doable for tabular values.
        """
        column_id = self.type.column_id(column_name)
        return list(map(lambda x: x[column_id], self.value))

    @staticmethod
    def from_flat_column(value):
        """
        Generates a (untyped) bundle from a flat column, packing each element into a single row
        """
        packed = list(map(lambda x: [x], value))
        return Bundle(value=packed)

    def with_type_if_missing(self, data_type: DataType):
        """
        Add a type if it's missing, returns a copy
        """
        use_type = data_type if self.type is None else self.type
        return Bundle(use_type, self.value)

    @staticmethod
    def decode(content_type: str, data, assumed_type: DataType = None):
        """
        Decode the bundle with the given content type from a file-like data object.
        If the content type is unknown, it will try to use JSON.
        """
        if content_type == MIME_MANTIK_JSON_BUNDLE:
            return Bundle.decode_json_bundle(data.read())

        if content_type == MIME_MSGPACK:
            return Bundle.decode_msgpack(data, assumed_type)

        if content_type == MIME_MANTIK_BUNDLE:
            return Bundle.decode_msgpack_bundle(data)

        return Bundle.decode_json(data.read(), assumed_type)

    @staticmethod
    def decode_json(json_str, assumed_type: DataType = None):
        parsed = json.loads(json_str)
        return Bundle(assumed_type, parsed)

    @staticmethod
    def decode_json_bundle(json_str):
        # Note: this is the new variant as discussed in https://gl.ambrosys.de/mantik/core/issues/2
        parsed = json.loads(json_str)  # Needed for DataType
        header = parsed.get("header")
        datatype = header.get("format")
        data_type = DataType(datatype)
        value = parsed.get("value")
        return Bundle(data_type, value)

    @staticmethod
    def decode_msgpack(data, assumed_type: DataType = None):
        unpacker = msgpack.Unpacker(file_like=data, raw=False)
        if assumed_type is None:
            is_tabular = True
        else:
            is_tabular = assumed_type.is_tabular()

        if is_tabular:
            return Bundle(assumed_type, list(unpacker))
        else:
            return Bundle(assumed_type, unpacker.next())

    @staticmethod
    def decode_msgpack_bundle(data):
        unpacker = msgpack.Unpacker(file_like=data, raw=False)
        header = unpacker.unpack()
        data_type_value = header.get("format")  # Must be present
        data_type = DataType(data_type_value)
        remaining = list(unpacker)
        if data_type.is_tabular():
            return Bundle(data_type, remaining)
        else:
            return Bundle(data_type, remaining.pop(0))

    def encode(self, content_type: str):
        if content_type == MIME_MANTIK_JSON_BUNDLE:
            return self.encode_json_bundle()

        if content_type == MIME_MSGPACK:
            return self.encode_msgpack()

        if content_type == MIME_MANTIK_BUNDLE:
            return self.encode_msgpack_bundle()

        return self.encode_json()

    def encode_json(self):
        return json.dumps(self.value)

    def encode_json_bundle(self):
        if self.type is None:
            raise Exception("No type available")

        value = {
            "header": {
                "format": self.type.representation
            },
            "value": self.value
        }
        return json.dumps(value)

    def encode_msgpack(self):
        if self.type is None:
            is_tabular = True
        else:
            is_tabular = self.type.is_tabular()
        packer = msgpack.Packer(autoreset=False)
        if is_tabular:
            for x in self.value:
                packer.pack(x)
        else:
            packer.pack(self.value)

        return packer.bytes()

    def encode_msgpack_bundle(self):
        if self.type is None:
            raise Exception("No type available")
        header = {
            "format": self.type.representation
        }
        packer = msgpack.Packer(autoreset=False)
        packer.pack(header)
        if self.type.is_tabular():
            for x in self.value:
                packer.pack(x)
        else:
            packer.pack(self.value)
        return packer.bytes()

    def __eq__(self, other):
        if isinstance(other, Bundle):
            return self.type == other.type and self.value == other.value
        else:
            return False

    def __repr__(self):
        return "Bundle({}, {})".format(self.type, self.value)