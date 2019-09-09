"""Computes y = 2*x on the sklearn stack via mantik."""

import numpy as np
from sklearn.datasets.samples_generator import make_blobs
from sklearn.metrics import accuracy_score

import mantik.engine
import mantik.types

data, label = make_blobs(n_samples=100, n_features=2, centers=2)

ds = """
{
    "columns":
    {
        "coordinates":
        {
            "type": "tensor",
            "shape": [2],
            "componentType": "float64"
        }
    }
}
"""
data_type = mantik.types.DataType.from_json(ds)
# TODO (mq): This reshaping is unintuitive
bundle = mantik.types.Bundle(data_type, data.reshape(-1, 1, 2).tolist())

with mantik.engine.Client("localhost", 8087) as client:
    kmeans = client._add_algorithm("bridge/sklearn/simple_learn/example/kmeans")
    with client.enter_session():
        [*_, trained_algorithm], stats = client.train([kmeans], bundle)
        kmeans_trained = client.tag(trained_algorithm, "kmeans_trained").save()
        result = client.apply([kmeans_trained.item], bundle).fetch()
        print(result)

prediction = np.array(result.bundle.value).reshape(-1)
score = accuracy_score(label, prediction)
print(f"Accuracy score: {score}")
