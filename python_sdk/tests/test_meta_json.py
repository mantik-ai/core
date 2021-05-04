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

from mantik.types import parse_and_decode_meta_json, parse_and_decode_meta_yaml, decode_meta_json
import json


def test_decode_meta_json_primitives():
    json_values = [
        "null",
        "true",
        "false",
        "[]",
        "{}",
        '"Hello"',
        "1",
        "5.5",
        "[1,2,3]",
        '{"Hello":"World","How":"Nice"}',
    ]
    for v in json_values:
        # Plain values
        parsed = json.loads(v)
        decoded = decode_meta_json(parsed)
        assert decoded == parsed

        # Wrapped in Objects
        in_object = '{"x":' + v + "}"
        parsed = json.loads(in_object)
        decoded = decode_meta_json(parsed)
        assert decoded == parsed


def test_parse_meta_json():
    sample = """
    {"metaVariables": [
        {
            "name": "foo",
            "type": "int32",
            "value": 123
        },
        {
            "name": "bar",
            "type": "bool",
            "value": true,
            "fix": true
        }
    ], 
    "a": "${foo}",
    "b": ["${bar}", "x", "$${escaped}"]
    }
    """
    expected = """
    {"metaVariables": [
        {
            "name": "foo",
            "type": "int32",
            "value": 123
        },
        {
            "name": "bar",
            "type": "bool",
            "value": true,
            "fix": true
        }
    ], 
    "a": 123,
    "b": [true, "x", "${escaped}"]
    }
    """
    decoded = parse_and_decode_meta_json(sample)
    expected = json.loads(expected)
    assert decoded == expected


def test_ignore_meta_block():
    bogus = """
    {"metaVariables": [
        {
            "name": "foo",
            "value": 123,
            "type": "int32",
            "unknown": "${foo}"
        }
    ]}
    """
    decoded = parse_and_decode_meta_json(bogus)
    expected = json.loads(bogus)
    assert decoded == expected


def test_decode_meta_yaml():
    yaml = """
metaVariables:
  - name: foo
    value: 123
    type: int32
x: ${foo}   
    """
    expected = """
    {
        "metaVariables":[
            {"name":"foo", "value": 123, "type": "int32"}
        ],
        "x": 123
    }
    """
    decoded = parse_and_decode_meta_yaml(yaml)
    parsed_expected = json.loads(expected)
    assert decoded == parsed_expected
