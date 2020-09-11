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
