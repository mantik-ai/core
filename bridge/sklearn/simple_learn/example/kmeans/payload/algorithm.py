import pickle

import numpy as np
from sklearn.cluster import KMeans

import mantik
import mantik.types


MODEL_FILE = "model.pickle"


def train(bundle: mantik.types.Bundle, meta: mantik.types.MetaVariables) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("coordinates")
    learn_data = np.array(coordinates)
    random_state = meta.get("random_state")
    meta = {k: meta.get(k) for k in meta.keys()}
    meta["random_state"] = int(random_state) if random_state != "null" else None
    model = KMeans(**meta).fit(learn_data)
    with open(MODEL_FILE, "wb") as f:
        pickle.dump(model, f)
    value = [[model.cluster_centers_.tolist(), model.inertia_, model.n_iter_]]
    return mantik.types.Bundle(value=value)


def try_init():
    with (open(MODEL_FILE, "rb")) as f:
        return pickle.load(f)


def apply(model, bundle: mantik.types.Bundle) -> mantik.types.Bundle:
    coordinates = bundle.flat_column("coordinates")
    data = np.array(coordinates)
    result = model.predict(data)
    return mantik.types.Bundle.from_flat_column(result.tolist())
