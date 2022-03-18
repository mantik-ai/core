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
import mantik.engine.engine as engine
import mantik.engine.objects as objects

import csv
import json
import logging
import io
import requests

logger = logging.getLogger(__name__)

def load_csv_from_file(client: engine.Client, path: str, skip_header: bool = True, comma: str = None,
             data_type: mantik.types.DataType = None) -> objects.MantikItem:
    """Load a CSV from File
    :param client: Engine client
    :param path: the CSV File
    :param skip_header: if true, the header will be skipped
    :param comma: comma character override (default is ',')
    :param data_type: the expected data type. If not given, strings will be assumed and
           either the first row is taken (when skip_header is true), or numbers
    """
    if data_type is None:
        with open(path) as f:
            data_type = _guess_csv_datatype(f, path, skip_header, comma)

    header_json = _create_header_json(data_type, skip_header, comma)

    logger.debug(f"Generated CSV Header {header_json}")

    with open(path, "rb") as file_in:
        return client.construct_item(header_json, file_in)



def load_csv_from_url(client: engine.Client, url: str, skip_header: bool = True, comma: str = None,
             data_type: mantik.types.DataType = None) -> objects.MantikItem:
    """Create an MantikItem from URL. Note: the URL will be put into the MantikHeader and the CSV bridge
    will download it by itself
    :param client: Engine client
    :param file: the CSV File
    :param skip_header: if true, the header will be skipped
    :param comma: comma character override (default is ',')
    :param data_type: the expected data type. If not given, strings will be assumed and
           either the first row is taken (when skip_header is true), or numbers
    """
    if data_type is None:
        with requests.get(url) as response:
            data_type = _guess_csv_datatype(response.text, url, skip_header, comma)

    header_json = _create_header_json(data_type, skip_header, comma, url)

    logger.debug(f"Generated CSV Header {header_json}")
    return client.construct_item(header_json)

def _create_header_json(data_type: mantik.types.DataType, skip_header: bool, comma: str, url: str = None) -> str:
    header = {
        'kind': 'dataset',
        'bridge': 'builtin/csv',
        'type': data_type.representation,
        'options': {
            'skipHeader': skip_header,
            'comma': comma
        }
    }
    if url is not None:
        header["url"] = url
    header_json = json.dumps(header)

    logger.debug(f"Generated CSV Header {header_json}")
    return header_json


def _guess_csv_datatype(stream: io.StringIO, name: str, skip_header: bool, comma: str = None) -> mantik.types.DataType:
    dialect = csv.unix_dialect
    if comma is not None:
        dialect.delimiter = comma
    reader = csv.reader(stream, dialect=dialect)
    first = next(reader)
    if skip_header:
        columns = dict.fromkeys(first, "string")
    else:
        columns = dict.fromkeys(range(1, len(first) + 1), "string")
    full = {"columns": columns}
    logger.debug(f"Guessed datatype of CSV {name} {full}")
    return mantik.types.DataType(full)
