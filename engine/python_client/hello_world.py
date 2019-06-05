from mantik.protobuf.engine.engine_pb2_grpc import *
from mantik.protobuf.engine.engine_pb2 import *

from mantik.protobuf.engine.sessions_pb2 import *
from mantik.protobuf.engine.sessions_pb2_grpc import *

from mantik.protobuf.engine.debug_pb2 import *
from mantik.protobuf.engine.debug_pb2_grpc import *

from mantik.protobuf.engine.graph_builder_pb2 import *
from mantik.protobuf.engine.graph_builder_pb2_grpc import *

from mantik.protobuf.engine.graph_executor_pb2 import *
from mantik.protobuf.engine.graph_executor_pb2_grpc import *

from mantik.protobuf.engine.ds_pb2 import *

from google.protobuf.empty_pb2 import Empty

channel = grpc.insecure_channel('localhost:8087')
about_service = AboutServiceStub(channel)
session_service = SessionServiceStub(channel)
debug_service = DebugServiceStub(channel)
graph_builder_service = GraphBuilderServiceStub(channel)
graph_executor_service = GraphExecutorServiceStub(channel)

response: VersionResponse = about_service.Version(Empty())
print("Connected to version {}".format(response.version))

add_response = debug_service.AddLocalMantikDirectory(
    AddLocalMantikDirectoryRequest(
        directory="bridge/tf/saved_model/test/resources/samples/double_multiply"
    )
)
print("Added item {}".format(add_response.name))


session = session_service.CreateSession(CreateSessionRequest())
print("Created session {}".format(session.session_id))

algorithm = graph_builder_service.Get(GetRequest(session_id=session.session_id, name="double_multiply"))
print("Created Algorithm Node {}".format(algorithm.item_id))

# It would be nicer to create the Bundle via the python shared library
# but this lead to name clashes (for mantik namespace) here.

dataset = graph_builder_service.Literal(
    LiteralRequest(
        session_id=session.session_id,
        bundle=Bundle(
            data_type=DataType(json='{"columns":{"x":"float64"}}'),
            encoding=ENCODING_JSON,
            encoded=b'[[1.0],[2.0]]'
        )
    )
)

print("Created Literal Node {}".format(dataset.item_id))

application_result = graph_builder_service.AlgorithmApply(
    ApplyRequest(
        session_id=session.session_id,
        algorithm_id=algorithm.item_id,
        dataset_id=dataset.item_id
    )
)
print("Application Result Node {}".format(application_result.item_id))

cache_result = graph_builder_service.Cached(
    CacheRequest(
        session_id=session.session_id,
        item_id=application_result.item_id
    )
)
print("Cache Result Node {}".format(cache_result.item_id))

execution_response = graph_executor_service.FetchDataSet(
    FetchItemRequest(
        session_id=session.session_id,
        dataset_id=cache_result.item_id,
        encoding=ENCODING_JSON
    )
)
response_bundle = execution_response.bundle
print("Execution Result Node {} {}".format(response_bundle.data_type.json, response_bundle.encoded))

session_service.CloseSession(CloseSessionRequest(session_id=session.session_id))
print("Closed session {}".format(session.session_id))
