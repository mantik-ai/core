#
# This file is part of the Mantik Project.
# Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
# Authors: See AUTHORS file
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License version 3.
#
# You should have received a copy of the GNU Affero General Public License
# along with this program.
#
# Additionally, the following linking exception is granted:
#
# If you modify this Program, or any covered work, by linking or
# combining it with other code, such other code is not for that reason
# alone subject to any of the requirements of the GNU Affero GPL
# version 3.
#
# You can be released from the requirements of the license by purchasing
# a commercial license.
#


"""Run sklearn.cluster.KMeans via mantik."""
import mantik.engine
import mantik.types
import time

import numpy as np
from sklearn.datasets import make_blobs
from sklearn.metrics import accuracy_score
from sklearn.metrics.pairwise import pairwise_distances_argmin
from sklearn.model_selection import train_test_split

centers = np.sort(np.array([[1, 1], [0, 0], [-1, -1]]), axis=0)
data, _ = make_blobs(n_samples=100, n_features=2, centers=centers, cluster_std=0.95)
label = pairwise_distances_argmin(data, centers)

train_data, test_data, train_label, test_label = train_test_split(data, label)
print("Using mantik...\n")
start_time = time.time()

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
train_bundle = mantik.types.Bundle(data_type, train_data.reshape(-1, 1, 2).tolist())
test_bundle = mantik.types.Bundle(data_type, test_data.reshape(-1, 1, 2).tolist())

n_clusters = len(centers)
meta = dict(n_clusters=n_clusters)

my_ref = "mq/kmeans_trained_on_blobs"

with mantik.engine.Client("localhost", 8087) as client:
    simple_learn = client._add_algorithm(
        "../../bridge/sklearn/simple_learn", named_mantik_id="mantik/sklearn.simple"
    )
    kmeans = client._add_algorithm("../../bridge/sklearn/simple_learn/example/kmeans")
    with client.enter_session():
        trained_pipe, stats = client.train(
            [kmeans], train_bundle, meta=meta, action_name="Training"
        )
        kmeans_trained = client.tag(trained_pipe, my_ref).save()
        train_result = client.apply(trained_pipe, train_bundle).fetch(
            action_name="Fetching Train Results"
        )
        test_result = client.apply(trained_pipe, test_bundle).fetch(
            action_name="Fetching Train Results"
        )

end_time = time.time()

# TODO (mq): bundle to pandas.DataFrame
centers_trained = np.sort(
    np.array(stats.bundle.value[0][0]).reshape(n_clusters, -1), axis=0
)
train_prediction = pairwise_distances_argmin(train_data, centers_trained)
test_prediction = pairwise_distances_argmin(test_data, centers_trained)

print("Labeled centers: ", centers)
print("Trained centers: ", centers_trained)
print("Inertia: ", stats.bundle.value[0][1])
print("Iterations: ", stats.bundle.value[0][2])
print(
    "Accuracy score on training data: ", accuracy_score(train_label, train_prediction)
)
print("Accuracy score on test data: ", accuracy_score(test_label, test_prediction))
print("Running kmeans via mantik took", end_time - start_time)

print("\n\n\n")
print("Using sklearn directly...\n")
start_time = time.time()
# ===============================================================
from sklearn.cluster import KMeans

model = KMeans(n_clusters=n_clusters).fit(train_data)
centers_trained = np.sort(model.cluster_centers_, axis=0)
# ===============================================================
end_time = time.time()

train_prediction = pairwise_distances_argmin(train_data, centers_trained)
test_prediction = pairwise_distances_argmin(test_data, centers_trained)

print("Labeled centers: ", centers)
print("Trained centers: ", centers_trained)
print("Inertia: ", model.inertia_)
print("Iterations: ", model.n_iter_)
print(
    "Accuracy score on training data: ", accuracy_score(train_label, train_prediction)
)
print("Accuracy score on test data: ", accuracy_score(test_label, test_prediction))
print("Running kmeans directly took", end_time - start_time)
# ===============================================================
