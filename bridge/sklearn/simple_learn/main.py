from mantik.bridge.bridge_app import start
from mantik.types import Mantikfile
from algorithm_wrapper import AlgorithmWrapper


def createAlgorithm(mantikfile: Mantikfile):
    algorithm = AlgorithmWrapper(mantikfile)
    algorithm.try_init_catching()
    return algorithm


start(createAlgorithm)
