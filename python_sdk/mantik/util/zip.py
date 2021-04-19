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
