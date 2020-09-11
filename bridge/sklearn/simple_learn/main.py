from mantik.bridge import start_mnp_bridge
from mantik.types import MantikHeader
from algorithm_wrapper import AlgorithmWrapper


def createAlgorithm(mantikheader: MantikHeader):
    algorithm = AlgorithmWrapper(mantikheader)
    algorithm.try_init_catching()
    return algorithm


start_mnp_bridge(createAlgorithm, "Sklearn Bridge")
