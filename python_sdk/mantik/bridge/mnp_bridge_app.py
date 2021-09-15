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

from typing import Callable

from mnp import Server

from mantik.types import MantikHeader
from .mnp_bridge_handler import MnpBridgeHandler
from . import kinds
import logging
from . import mnp_bridge_handler
import mantik.types

BridgeProvider = Callable[[MantikHeader], kinds.Bridge]


def start_mnp_bridge(bridge_provider: BridgeProvider, name: str):
    """Entry point for simple algorithm based Python Bridges.

    :param bridge_provider:
    :param name:

    :return: None

    """

    def quit_handler():
        nonlocal server
        server.stop()

    logging.basicConfig()
    logging.getLogger().setLevel(logging.DEBUG)

    handler = MnpBridgeHandler(
        bridge_provider=bridge_provider, 
        name=name, 
        quit_handler=quit_handler,
    )
    server = Server(handler)
    address = "0.0.0.0:8502"
    server.start(address)
    print(f"Starting MNP Server for {name} on {address}")
    server.wait_for_termination()
