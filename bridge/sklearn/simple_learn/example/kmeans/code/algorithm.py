from sklearn.cluster import KMeans
import pickle
import numpy as np
import mantik

MODEL_FILE = "model.pickle"


def train(bundle: mantik.Bundle) -> mantik.Bundle:
    coordinates = bundle.flat_column("coordinates")
    learn_data = np.array(coordinates)
    cluster_count = 2  # TODO Should be meta variable.
    model = KMeans(n_clusters=cluster_count).fit(learn_data)
    with (open(MODEL_FILE, 'wb')) as f:
        pickle.dump(model, f)
    return mantik.Bundle()


def try_init():
    with(open(MODEL_FILE, "rb")) as f:
        return pickle.load(f)


def apply(model, bundle: mantik.Bundle) -> mantik.Bundle:
    coordinates = bundle.flat_column("coordinates")
    data = np.array(coordinates)
    result = model.predict(data)
    return mantik.Bundle.from_flat_column(result.tolist())