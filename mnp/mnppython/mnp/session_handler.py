import abc
from mnp.elements import PortConfiguration
from io import RawIOBase
from typing import List, BinaryIO


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
