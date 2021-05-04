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

from mantik import MantikHeader
from .algorithm import Algorithm
from .mnp_bridge_handler import MnpBridgeHandler
import logging

AlgorithmProvider = Callable[[MantikHeader], Algorithm]


def start_mnp_bridge(algorithm_provider: AlgorithmProvider, name: str):
    """
    Entry point for simple algorithm based Python Bridges
    :param algorithm_provider:
    :param name:
    :return:
    """

    def quit_handler():
        nonlocal server
        server.stop()

    logging.basicConfig()
    logging.getLogger().setLevel(logging.DEBUG)

    handler = MnpBridgeHandler(algorithm_provider, name, quit_handler)
    server = Server(handler)
    addr = "0.0.0.0:8502"
    server.start("0.0.0.0:8502")
    print("Starting MNP Server for {} on {}".format(name, addr))
    server.wait_for_termination()
