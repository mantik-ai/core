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
