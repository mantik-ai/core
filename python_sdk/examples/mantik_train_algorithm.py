"""Computes y = 2*x on the sklearn stack via mantik."""

import mantik.engine
import mantik.types

from sklearn.datasets.samples_generator import make_blobs
from sklearn.model_selection import train_test_split

data, label = make_blobs(n_samples=10, n_features=2, centers=2)
train_data, test_data, train_label, test_label = train_test_split(data, label)

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
print(train_data)
bundle = mantik.types.Bundle(data_type, train_data.tolist())
print(bundle.encode_json_bundle())
exit()
with mantik.engine.Client("localhost", 8087) as client:
    # TODO (mq): We should be able to search/list all existing algorithms.
    kmeans = client._add_algorithm("bridge/sklearn/simple_learn/example/kmeans")
    with client.enter_session():
        result = client.train([kmeans], bundle)
        print(result)
