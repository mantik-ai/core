import tempfile
import zipfile
import pytest
from typing import List

import mantik.util.zip as zip_utils

def _write_test_files(directory: str, names: List[str], contents: List[str]):
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