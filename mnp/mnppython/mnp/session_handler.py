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

import abc
from mnp.elements import PortConfiguration, OutputPortConfiguration
from mnp.collectable_byte_buffer import CollectableByteBuffer
from typing import List, BinaryIO, cast
import io


class SessionHandler(metaclass=abc.ABCMeta):
    """
    MNP SessionHandler
    """

    def __init__(self, session_id: str, ports: PortConfiguration):
        self.session_id = session_id
        self.ports = ports

    @abc.abstractmethod
    def quit(self):
        """
        Quit the session
        """

    @abc.abstractmethod
    def run_task(self, task_id: str, inputs: List[BinaryIO], outputs: List[BinaryIO]):
        """
        run a task
        """

    def run_task_with_bytes(self, task_id: str, inputs: List[bytes]) -> List[bytes]:
        """
        run a task with in memory byte representation
        (convenience method)
        """

        def open_reader(b: bytes) -> BinaryIO:
            return io.BytesIO(b)

        input_readers = list(map(open_reader, inputs))

        def open_writer(content_type: OutputPortConfiguration) -> CollectableByteBuffer:
            return CollectableByteBuffer()

        output_writers = list(map(open_writer, self.ports.outputs))
        self.run_task(task_id, input_readers, cast(List[BinaryIO], output_writers))

        def read_result(writer: CollectableByteBuffer) -> bytes:
            return writer.fetch_collected_bytes()

        results = list(map(read_result, output_writers))
        return results
