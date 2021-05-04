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

from typing import List
from typing import BinaryIO
from mnp._stubs.mantik.mnp.mnp_pb2 import TaskPortStatus
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
        self.calls = 0
        self.byte_count = 0
        self.is_done = False

    def write(self, b: bytes):
        """
        Write something on the incoming side
        """
        result = self.writer.write(b)
        self.calls += 1
        self.byte_count += len(b)
        return result

    def write_eof(self):
        """
        Close incoming side
        """
        result = self.writer.close()
        self.calls += 1
        self.is_done = True
        return result

    def read(self) -> bytes:
        """
        Read out side
        """
        result = self.reader.read(MAX_CHUNK_SIZE)
        self.calls += 1
        self.byte_count += len(result)
        if not result:
            self.is_done = True
        return result

    def task_port_status(self) -> TaskPortStatus:
        # No error support here
        return TaskPortStatus(
            msg_count=self.calls,
            data=self.byte_count,
            done=self.is_done
        )


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
        return self.inputs[port].write(b)

    def push_eof(self, port: int):
        return self.inputs[port].write_eof()

    def pull(self, port: int) -> bytes:
        return self.outputs[port].read()

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

    def input_port_status(self) -> List[TaskPortStatus]:
        """
        Reports input port status in an MNP Compatible way
        """
        return list(map(StreamyPipe.task_port_status, self.inputs))

    def output_port_status(self) -> List[TaskPortStatus]:
        """
        Report output port status in a MNP Compatible way.
        """
        return list(map(StreamyPipe.task_port_status, self.outputs))