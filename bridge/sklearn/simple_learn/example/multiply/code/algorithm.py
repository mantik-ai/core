import mantik.types
import numpy as np

def train(bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    return mantik.types.Bundle()

def try_init():
    return

def apply(model, bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("x")
    data = np.array(coordinates)
    result = 2*data
    return mantik.types.Bundle.from_flat_column(result.tolist())
