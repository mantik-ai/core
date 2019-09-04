"""Computes y = 2*x on the sklearn stack via mantik."""

import logging

import mantik.engine
import mantik.types

logging.basicConfig(level=logging.INFO)

columns = mantik.types.DataType.from_kw(x="float64")
bundle = mantik.types.Bundle(columns, [[1.0], [2.0]])
pipe = ["multiply"]
pipe2 = ["select x * CAST(2 as float64) as y"]

with mantik.engine.Client("localhost", 8087) as client:
    # TODO (mq): We should be able to search/list all existing algorithms.
    client._add_algorithm("bridge/sklearn/simple_learn/example/multiply")
    with client.enter_session():
        result = client.apply(pipe, bundle).fetch()
        print(f"Result: {result.bundle}")
        # you can explicitly upload a bundle.
        # however, just passing the bundle works, too!
        data = client.upload_bundle(bundle)
        result2 = client.apply(pipe2, data).fetch()
        print(f"Result2: {result2.bundle}")

assert result.bundle == result2.bundle


<<<<<<< HEAD
<<<<<<< HEAD
tagged_application_result = graph_builder_service.Tag(TagRequest(
    session_id=session.session_id,
    item_id=application_result.item_id,
    named_mantik_id="mein_item"
))

save_response = graph_executor_service.SaveItem(
    SaveItemRequest(
        session_id=session.session_id,
        item_id=tagged_application_result.item_id
    )
)
print("Saved item as {}".format(save_response.mantik_item_id))

session_service.CloseSession(CloseSessionRequest(session_id=session.session_id))
print("Closed session {}".format(session.session_id))
=======
print(f"Execution Result Node {result.data_type.json} {result.encoded}")
>>>>>>> hide protobuf code in python wrapper
=======
>>>>>>> convert between different bundle types
