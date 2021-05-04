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

from mnp.streammultiplexer import StreamyPipe
import threading
import pytest


def test_simple_pipe_simple():
    p = StreamyPipe()
    p.writer.write(b"Hello World")
    p.writer.close()
    read = p.reader.read()
    assert read == b"Hello World"


def test_huge_data():
    alot = 1000000
    p = StreamyPipe()

    def writeAlot():
        for i in range(alot):
            p.writer.write(b"A")
        p.writer.close()

    t = threading.Thread(target=writeAlot)
    t.start()

    consume = p.reader.read()

    t.join()
    assert len(consume) == alot
