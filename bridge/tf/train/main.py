from mantik import Bundle, Mantikfile
from mantik.bridge import Algorithm
from mantik.bridge.bridge_app import start
from algorithm_wrapper import AlgorithmWrapper

def create_algorithm(mantikfile: Mantikfile) -> Algorithm:
    return AlgorithmWrapper(mantikfile)


start(create_algorithm)