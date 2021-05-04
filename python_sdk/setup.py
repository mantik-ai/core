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

import pathlib
from setuptools import setup, find_packages


def read(fname, split=True):
    with open(fname, "r") as f:
        content = f.read()
    return content.split("\n") if split else content


req = pathlib.Path(__file__).parent / "requirements.txt"

if __name__ == "__main__":
    setup(
        name="mantik",
        use_scm_version=True,
        description=__doc__,
        author="Mantik Team",
        author_email="info@mantik.ai",
        url="https://www.mantik.ai",
        install_requires=read(str(req)),
        python_requires=">=3.7",
        zip_safe=False,
        packages=find_packages(exclude=("tests",)),
    )
