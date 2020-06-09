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
