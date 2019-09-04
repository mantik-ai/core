"""Computes y = 2*x on the sklearn stack via mantik."""

import logging

import mantik.engine
import mantik.types

logging.basicConfig(level=logging.INFO)

columns = mantik.types.DataType.from_kw(x="float64")
bundle = mantik.types.Bundle(columns, [[1.0], [2.0]])
steps = ["multiply", "select y as x", "multiply"]
# steps = ["select x as y"]

with mantik.engine.Client("localhost", 8087) as client:
    # TODO (mq): We should be able to search/list all existing algorithms.
    client._add_algorithm("bridge/sklearn/simple_learn/example/multiply")
    with client.enter_session():
        pipeline = client.make_pipeline(steps=steps, bundle=bundle)
        print(pipeline)
        result = client.apply(pipeline, bundle).compute()

print(f"Result: {result.bundle}")


# def flat(x):
#     import itertools
#     return itertools.chain.from_iterable(x)
#
#
# assert all(2*x == y for x, y in zip(flat(bundle.value), flat(result.bundle.value)))

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
