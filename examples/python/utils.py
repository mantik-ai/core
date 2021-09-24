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

def convert_relative_to_absolute_path(current_path: str=__file__, relative_path: str= "../../bridge"):
    """
    Get the absolute path from any given relative path.

    :param current_path: The current filepath.
    :param relative_path: Path relative to current_path.
    :return: Absolute path.

    Note: This function solves the problem that when using relative paths in scripts, those scripts cannot be executed from any location.
    """
    if os.path.isdir(current_path):
        current_abspath = os.path.abspath(current_path)
    else:
        current_abspath = os.path.abspath(current_path).rsplit("/",1)[0] #Remove file name from dir path
    absolute_path = os.path.join(current_abspath, relative_path)
    return absolute_path