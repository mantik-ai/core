from mantik.bridge.bridge_app import start
from mantik.types import MantikHeader
from algorithm_wrapper import AlgorithmWrapper


def createAlgorithm(mantikheader: MantikHeader):
    algorithm = AlgorithmWrapper(mantikheader)
    algorithm.try_init_catching()
    return algorithm


start(createAlgorithm)
