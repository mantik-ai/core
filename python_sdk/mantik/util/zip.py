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

import os
import zipfile
import logging

import tempfile

logger = logging.getLogger(__name__)


def zip_directory(directory: str, writer, avoid_hidden=False):
    with zipfile.ZipFile(writer, "w") as zipwriter:
        for root, dirs, files in os.walk(directory):
            basename = os.path.basename(root)
            if avoid_hidden:
                if basename == "__pycache__":
                    continue
                if basename.startswith("."):
                    continue
            for file in files:
                bn = os.path.basename(file)
                if avoid_hidden:
                    if bn.startswith("."):
                        continue
                fp = os.path.join(root, file)
                zp = os.path.relpath(fp, directory)
                logger.debug(f"Zipping {fp} -> {zp}")
                zipwriter.write(fp, zp)
        zipwriter.close()


def get_zip_bit_representation(directory: str, avoid_hidden=False):
    with tempfile.TemporaryDirectory() as tmp_dir:
        zip_dir = zip_directory(
            directory, writer=tmp_dir + "/zip_temp", avoid_hidden=avoid_hidden
        )
        with open(tmp_dir + "/zip_temp", "rb") as f:
            content = f.read()
    return content
