from mantik.types import MantikHeader, Bundle
from mantik.bridge import Algorithm
from mantik.bridge.bridge_app import start
from algorithm_wrapper import AlgorithmWrapper


def create_algorithm(mantikheader: MantikHeader) -> Algorithm:
    return AlgorithmWrapper(mantikheader)


start(create_algorithm)
