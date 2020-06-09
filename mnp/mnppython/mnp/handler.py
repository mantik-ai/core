import abc
from google.protobuf.any_pb2 import Any
from mnp.session_handler import SessionHandler
from mnp.elements import *
from typing import Callable, List, Iterator


class Handler(metaclass=abc.ABCMeta):
    """
    A MNP Handler
    """

    @abc.abstractmethod
    def about(self) -> AboutResponse:
        """Responses to About call"""

    @abc.abstractmethod
    def quit(self):
        """Quit requested"""

    @abc.abstractmethod
    def init_session(self, session_id: str,
                     configuration: Any,
                     ports: PortConfiguration,
                     callback: Callable[[SessionState], None] = None) -> SessionHandler:
        """Initialize a session"""
