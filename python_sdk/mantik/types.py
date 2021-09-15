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
import copy
import dataclasses
import json
import os
import typing as t

import msgpack
import yaml

Representation = t.Union[dict, str]

META_PREFIX = "${"
META_SUFFIX = "}"

MIME_JSON = "application/json"
MIME_MANTIK_JSON_BUNDLE = "application/x-mantik-bundle-json"
MIME_MSGPACK = "application/x-msgpack"
MIME_MANTIK_BUNDLE = "application/x-mantik-bundle"
MIME_TYPES = [MIME_JSON, MIME_MANTIK_JSON_BUNDLE, MIME_MANTIK_BUNDLE, MIME_MSGPACK]


class UnsupportedBridgeKindError(Exception):
    """A given bridge kind is not available in mantik."""


@dataclasses.dataclass
class DataType:
    """Mantik ds.DataType.

    Note:
        This is just a thin wrapper around the parsed JSON/YAML Representation.

    Note:
        In column declarations the insertion order is important
        As of Python 3.7 dicts preserve order (see https://docs.python.org/3/whatsnew/3.7.html )
        Before that it should also be the case, but wasn't part of the standard.
    """

    representation: Representation
    """Initializes from the parsed JSON representation."""

    @classmethod
    def from_kw(cls, **kwargs):
        return cls(dict(columns=kwargs))

    @classmethod
    def from_json(cls, json_string) -> DataType:
        """Construct DataType from its json representation.

        Args:
            json_string: json representation

        Note:
            Column representation needs to be a OrderedDict in practice.

        """
        return cls(json.loads(json_string))

    @property
    def is_tabular(self) -> bool:
        """Returns true if the type is tabular."""
        return self.sub_type == "tabular"

    @property
    def sub_type(self):
        """Returns the sub type of the data type.

        This is either fundamental or the "type" element of the JSON representation

        """
        if isinstance(self.representation, dict):
            return self.representation.get("type", "tabular")
        elif isinstance(self.representation, str):
            return "fundamental"
        raise ValueError("Invalid representation type")

    def to_json(self) -> str:
        return json.dumps(self.representation)

    def column_id(self, column_name: str) -> int:
        """
        Returns the index of a column, data type must be tabular.
        :returns the index of the column or raise an exception
        """
        if not self.is_tabular:
            raise ValueError("Column names are only set for tabular data.")
        columns = self.representation.get("columns")
        for index, (k, _) in enumerate(columns.items()):
            if k == column_name:
                return index
        raise Exception("Column {} not found".format(column_name))

    @property
    def column_names(self) -> t.List[str]:
        """Return the column names."""
        if not self.is_tabular:
            raise ValueError("Column names are only set for tabular data.")
        columns = self.representation.get("columns")
        return list(columns.keys())

    def column_type(self, name: str) -> DataType:
        """Return the Column type as data type.

        Must be tabular.

        """
        if not self.is_tabular:
            raise ValueError("Column names are only set for tabular data.")
        columns = self.representation.get("columns")
        return DataType(columns.get(name))

    def __repr__(self):
        return str(self.representation)


@dataclasses.dataclass
class FunctionType(abc.ABC):
    """Function mapping.
    
    Represents the mapping of a bridge. E.g. a dataset bridge maps
    no input to an output, algorithms map an input to an output.

    """

    output: DataType

    @classmethod
    @abc.abstractmethod
    def from_dict(cls, parsed_yaml: dict) -> FunctionType:
        """Generates a function from the result of JSON Parsing."""

    @abc.abstractmethod
    def to_json(self) -> dict:
        """Serializes to json."""


@dataclasses.dataclass
class DataSetFunctionType(FunctionType):
    """Function mapping for a dataset."""

    def __repr__(self):
        return f"None -> {self.output}"

    @classmethod
    def from_dict(cls, parsed_yaml: dict) -> AlgorithmFunctionType:
        """Generates a function from the result of JSON Parsing.
        
        The MantikHeader for a dataset does not expect an explicit output
        definition of the form
        ```YAML
        type:
          output:
            ...
        ```
        Instead, for a dataset a reduced output definition like
        ```YAML
        type:
          ...
        ```
        must be used.

        """
        return cls(output=DataType(parsed_yaml))

    def to_json(self) -> str:
        """Serializes to json."""
        return json.dumps({"output": self.output.representation})


@dataclasses.dataclass
class AlgorithmFunctionType(FunctionType):
    """Function mapping for an algorithm."""

    input: DataType

    def __repr__(self):
        return f"{self.input} -> {self.output}"

    @classmethod
    def from_dict(cls, parsed_yaml: dict) -> AlgorithmFunctionType:
        """Generates a function from the result of JSON Parsing.
        
        The MantikHeader for an algorithm expects an explicit definiton
        for both input and output of the form
        ```YAML
        type:
          input:
            ...
          output:
            ...
        ```

        """
        return cls(
            input=DataType(parsed_yaml.get("input")),
            output=DataType(parsed_yaml.get("output")),
        )

    def to_json(self) -> str:
        """Serializes to json."""
        return json.dumps({"input": self.input.representation, "output": self.output.representation})


@dataclasses.dataclass
class MantikHeader:
    """Represents the MantikHeader which controls the way a algorithm/dataset works."""

    # TODO: The base dir should not be part of a MantikHeader

    yaml_code: dict
    basedir: str
    name: str
    type: FunctionType
    meta_variables: MetaVariables
    kind: str
    training_type: t.Optional[DataType]
    stat_type: t.Optional[DataType]

    @classmethod
    def from_yaml(cls, parsed_yaml: dict, basedir: str) -> MantikHeader:
        """Contruct MantikHeader from parsed yaml."""
        name = parsed_yaml.get("name", None)
        kind = parsed_yaml.get("kind")
        type = _create_function_type_from_bridge_kind(
            kind=kind,
            parsed_yaml=parsed_yaml,
        )
        meta = MetaVariables.from_parsed(parsed_yaml)
        tt = parsed_yaml.get("trainingType", None)
        training_type = DataType(tt) if tt is not None else None
        st = parsed_yaml.get("statType", None)
        stat_type = DataType(st) if st is not None else None

        return cls(
            yaml_code=parsed_yaml,
            basedir=basedir,
            name=name,
            type=type,
            meta_variables=meta,
            kind=kind,
            training_type=training_type,
            stat_type=stat_type,
        )

    @property
    def has_training(self) -> bool:
        return self.training_type is not None

    @classmethod
    def load(cls, file: str) -> MantikHeader:
        """Load a local mantik header."""
        with open(file) as f:
            return MantikHeader.parse(f.read(), os.path.dirname(file))

    @classmethod
    def parse(cls, content: str, root_dir: str) -> MantikHeader:
        return MantikHeader.from_yaml(parse_and_decode_meta_yaml(content), root_dir)

    @property
    def payload_dir(self):
        """Returns the payload directory."""
        return os.path.join(self.basedir, "payload")


def _create_function_type_from_bridge_kind(kind: str, parsed_yaml: dict) -> FunctionType:
    type_definition = parsed_yaml.get("type")
    if kind == "dataset":
        return DataSetFunctionType.from_dict(type_definition)
    elif kind == "algorithm" or kind == "trainable":
        return AlgorithmFunctionType.from_dict(type_definition)
    raise UnsupportedBridgeKindError(
        f"{kind} not supported for bridge MantikHeader.kind"
    )


def parse_and_decode_meta_json(meta_json):
    """
    Parse and Decode Meta JSON
    :param meta_json: JSON code
    """
    parsed = json.loads(meta_json)
    return decode_meta_json(parsed)


def parse_and_decode_meta_yaml(meta_yaml):
    """
    Parse and Decode Meta YAML
    :param meta_yaml:
    :return:
    """
    parsed = yaml.load(meta_yaml, Loader=yaml.Loader)
    return decode_meta_json(parsed)


def decode_meta_json(parsed_json):
    """
    Decode metaVariables from the loaded JSON block
    and fills interpolated places.
    :param parsed_json plain python dict, containing the parsed json.
    :return:
    """
    if not isinstance(parsed_json, dict):
        # No metaVariables block possible.
        return copy.deepcopy(parsed_json)

    meta_variables = MetaVariables.from_parsed(parsed_json)

    result = dict()

    for k, v in parsed_json.items():
        if k != "metaVariables":
            result[k] = _decode_meta_json_value(v, meta_variables)
        else:
            result[k] = copy.deepcopy(v)
    return result


def _decode_meta_json_value(v, meta_variables: MetaVariables):
    if isinstance(v, dict):
        return _decode_meta_json_dict(v, meta_variables)
    if isinstance(v, list):
        return _decode_meta_json_list(v, meta_variables)
    if isinstance(v, str):
        return _decode_meta_json_string(v, meta_variables)
    return copy.copy(v)


def _decode_meta_json_dict(o: dict, meta_variables: MetaVariables):
    result = dict()
    for k, v in o.items():
        result[k] = _decode_meta_json_value(v, meta_variables)
    return result


def _decode_meta_json_list(o: list, meta_variables: MetaVariables):
    result = list()
    for v in o:
        result.append(_decode_meta_json_value(v, meta_variables))
    return result


def _decode_meta_json_string(o: str, meta_variables: MetaVariables):
    if o.startswith("$$"):
        # Escaped
        return o[1:]
    if o.startswith(META_PREFIX) and o.endswith(META_SUFFIX):
        variable_name = (o[len(META_PREFIX) :])[: -len(META_SUFFIX)]
        value = meta_variables.get(variable_name)
        return copy.copy(value)
    else:
        return copy.copy(o)


@dataclasses.dataclass
class MetaVariable:
    """A Meta variable as in the metaVariables block inside Meta Json."""

    bundle: Bundle
    """MetaVariable value"""

    name: str
    """Name of the MetaVariable."""

    fix: bool = False
    """If true, the value may not be changed anymore (usually not relevant for Bridges)"""

    @classmethod
    def from_json(cls, parsed_json):
        return cls(
            Bundle.decode_parsed_json_bundle(parsed_json),
            str(parsed_json.get("name")),
            bool(parsed_json.get("fix", False)),
        )


class MetaVariables(dict):
    """Contains multiple meta variables."""

    @classmethod
    def from_parsed(cls, parsed_json, key="metaVariables", default=None):
        """Construct a MetaVariable t.List from parsed json."""
        default = default or ""
        variables = [MetaVariable.from_json(var) for var in parsed_json.get(key, default)]
        return MetaVariables({var.name: var for var in variables})

    def get(self, key: str, default=None):
        try:
            return self[key].bundle.value
        except KeyError:
            if default is None:
                raise ValueError(f"No meta variable called {key} found and no default given")
            return default


@dataclasses.dataclass
class Bundle:
    """A Mantik Bundle (Datatype and Value)."""

    type: t.Optional[DataType] = None
    value: t.Optional[t.Any] = None

    def flat_column(self, column_name: str):
        """Select a single column and returns a flat list of each value.

        Only doable for tabular values.

        """
        column_id = self.type.column_id(column_name)
        return [x[column_id] for x in self.value]

    @classmethod
    def from_flat_column(cls, value):
        """Generates a (untyped) bundle from a flat column, packing each element into a single row."""
        return cls(value=[[x] for x in value])

    def __add__(self, data_type: DataType):
        """Add a type if it's missing, returns a copy."""
        use_type = data_type if self.type is None else self.type
        return self.__class__(use_type, self.value)

    @classmethod
    def decode(cls, content_type: str, data, assumed_type: DataType = None) -> Bundle:
        """Decode the bundle with the given content type from a file-like data object.

        If the content type is unknown, it will try to use JSON.

        """
        if content_type == MIME_MANTIK_JSON_BUNDLE:
            return cls.decode_json_bundle(data.read())

        if content_type == MIME_MSGPACK:
            return cls.decode_msgpack(data, assumed_type)

        if content_type == MIME_MANTIK_BUNDLE:
            return cls.decode_msgpack_bundle(data)

        return cls.decode_json(data.read(), assumed_type)

    @classmethod
    def decode_json(cls, json_str: str, assumed_type: DataType = None):
        parsed = json.loads(json_str)
        return cls(assumed_type, parsed)

    @classmethod
    def decode_json_bundle(cls, json_str):
        parsed = json.loads(json_str)  # Needed for DataType
        return cls.decode_parsed_json_bundle(parsed)

    @classmethod
    def decode_parsed_json_bundle(cls, parsed):
        data_type = DataType(parsed.get("type"))
        value = parsed.get("value")
        return cls(data_type, value)

    @classmethod
    def decode_msgpack(cls, data, assumed_type: DataType = None):
        unpacker = msgpack.Unpacker(file_like=data, raw=False)
        is_tabular = True if assumed_type is None else assumed_type.is_tabular
        value = list(unpacker) if is_tabular else unpacker.next()
        return cls(assumed_type, value)

    @classmethod
    def decode_msgpack_bundle(cls, data):
        unpacker = msgpack.Unpacker(file_like=data, raw=False)
        header = unpacker.unpack()
        data_type = DataType(header["format"])
        remaining = list(unpacker)
        value = remaining if data_type.is_tabular else remaining.pop(0)
        return cls(data_type, value)

    def encode(self, content_type: str):
        if content_type == MIME_MANTIK_JSON_BUNDLE:
            return self.encode_json_bundle()

        if content_type == MIME_MSGPACK:
            return self.encode_msgpack()

        if content_type == MIME_MANTIK_BUNDLE:
            return self.encode_msgpack_bundle()

        return self.encode_json()

    def encode_json(self):
        return _jsonify_value(self.value)

    def encode_json_bundle(self):
        if self.type is None:
            raise Exception("No type available")

        return _jsonify_bundle(type=self.type.representation, value=self.value)

    def encode_msgpack(self) -> bytes:
        if self.type is None:
            is_tabular = True
        else:
            is_tabular = self.type.is_tabular
        packer = msgpack.Packer(autoreset=False)
        if is_tabular:
            for x in self.value:
                packer.pack(x)
        else:
            packer.pack(self.value)

        return packer.bytes()

    def encode_msgpack_bundle(self) -> bytes:
        if self.type is None:
            raise Exception("No type available")
        header = {"format": self.type.representation}
        packer = msgpack.Packer(autoreset=False)
        packer.pack(header)
        if self.type.is_tabular:
            for x in self.value:
                packer.pack(x)
        else:
            packer.pack(self.value)
        return packer.bytes()

    def __len__(self):
        return len(self.value) if isinstance(self.value, list) else 1


@dataclasses.dataclass
class StreamableBundle(Bundle):
    """Bundle implementing a streamable (t.Iterator) as value."""

    value: t.Optional[t.Iterator] = None

    def encode_json(self) -> t.Iterator:
        for value in self.value:
            yield _jsonify_value(value)

    def encode_json_bundle(self) -> t.Iterator:
        if self.type is None:
            raise Exception("No type available")

        for value in self.value:
            yield _jsonify_bundle(type=self.type.representation, value=value)


def _jsonify_value(value: t.Any) -> str:
    return json.dumps(value, ensure_ascii=False).encode("utf8")


def _jsonify_bundle(type: Representation, value: t.Any) -> str:
    return json.dumps({"type": type, "value": value})
