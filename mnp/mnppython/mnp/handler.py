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
