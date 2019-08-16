import logging

from mantik.engine import *

logging.basicConfig(level=logging.DEBUG)

bundle = Bundle(
    data_type=DataType(json='{"columns":{"x":"float64"}}'),
    encoding=ENCODING_JSON,
    encoded=b"[[1.0],[2.0]]",
)

with Client("localhost", 8087) as client:
    client._add_algorithm("bridge/sklearn/simple_learn/example/multiply")
    with client.enter_session():
        promise = client.apply("multiply", bundle)
        result = promise.compute().bundle

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
