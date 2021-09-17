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

import tempfile
import zipfile
import pytest
import typing as t

import mantik.util.zip as zip_utils

def _write_test_files(directory: str, names: t.List[str], contents: t.List[str]):
    for name, content in zip(names, contents):
        with open(directory+"/"+name, "w") as f:
            f.write(content)

#TODO parametrize files and avoid_hidden (or only avoid hidden)
#@pytest.mark.parametrize([])
@pytest.mark.parametrize("avoid_hidden, expected_len", [(True, 1), (False, 2)])
def test_zip_directory(avoid_hidden, expected_len, tmpdir): #tmpdir is a builtin pytest fixture
    names = ["test", ".test_hidden"]
    contents = ["Test", "Hidden test"]
    zipfile_name = tmpdir+"/test.zip"
    with tempfile.TemporaryDirectory() as file_dir: # Second temp dir is needed to avoid recursion in zipping
        _write_test_files(file_dir, names, contents)
        zip_utils.zip_directory(directory=file_dir, writer=zipfile_name, avoid_hidden=avoid_hidden)
        with zipfile.ZipFile(zipfile_name, "r") as archive:
            assert len(archive.filelist) == expected_len

def test_get_zip_bit_representation():
    # Writing itself is tested above; only assert that something is there
    content = zip_utils.get_zip_bit_representation(".")
    assert content
