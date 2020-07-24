from mantik.types import MantikHeader
from mantik.bridge import Algorithm
from mantik.bridge import start_mnp_bridge
from algorithm_wrapper import AlgorithmWrapper


def create_algorithm(mantikheader: MantikHeader) -> Algorithm:
    return AlgorithmWrapper(mantikheader)


start_mnp_bridge(create_algorithm, "TfTrain Bridge")
