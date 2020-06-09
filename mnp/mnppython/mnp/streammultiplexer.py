from typing import List
from typing import BinaryIO
import io

import os

# Max Chunk size to aovid deadlock via Pipes
MAX_CHUNK_SIZE = 8192


class StreamyPipe:
    def __init__(self):
        read_fd, write_fd = os.pipe()
        self.read_fd = read_fd
        self.write_fd = write_fd
        self.writer = io.open(write_fd, "wb")
        self.reader = io.open(read_fd, "rb")


class StreamMultiplexer:
    """
    Handles input and output streams of a Task
    """

    inputs: List[StreamyPipe]
    outputs: List[StreamyPipe]

    def __init__(self, input_num: int, output_num: int):
        self.inputs = [StreamyPipe() for i in range(input_num)]
        self.outputs = [StreamyPipe() for i in range(output_num)]

    def wrapped_inputs(self) -> List[BinaryIO]:
        return [i.reader for i in self.inputs]

    def wrapped_outputs(self) -> List[BinaryIO]:
        return [i.writer for i in self.outputs]

    def push(self, port: int, b: bytes):
        self.inputs[port].writer.write(b)

    def push_eof(self, port: int):
        self.inputs[port].writer.close()

    def pull(self, port: int) -> bytes:
        return self.outputs[port].reader.read(MAX_CHUNK_SIZE)

    def output_reader(self, port: int) -> BinaryIO:
        return self.outputs[port].reader

    def close_output(self):
        for output in self.outputs:
            output.writer.close()

    def close(self):
        for output in self.outputs:
            output.writer.close()
        for input in self.inputs:
            input.writer.close()
