from tests.test_mnp_roundtrip import dummy_server_handler
from tests.dummy_handler import DummyHandler
from mnp._stubs.mantik.mnp.mnp_pb2_grpc import MnpServiceStub
from mnp._stubs.mantik.mnp.mnp_pb2 import *
from typing import Tuple
from mnp import PortConfiguration, InputPortConfiguration, OutputPortConfiguration
from mnp import Server
import grpc
import pytest


def test_simple_data_roundtrip(dummy_server_handler: Tuple[MnpServiceStub, DummyHandler]):
    stub, handler = dummy_server_handler

    port_config = PortConfiguration(
        [InputPortConfiguration("content1")], [OutputPortConfiguration("content2")]
    )

    response = list(stub.Init(InitRequest(
        session_id="session1",
        inputs=port_config.inputs_as_proto(),
        outputs=port_config.outputs_as_proto()
    )))

    # Last result must be ready
    assert response[-1].state == SS_READY

    input_data = [
        PushRequest(session_id="session1", task_id="task1", port=0),
        PushRequest(data=b"Hello"),
        PushRequest(data=b" "),
        PushRequest(data=b"World"),
        PushRequest(done=True)
    ]

    stub.Push(iter(input_data))

    response = list(stub.Pull(PullRequest(session_id="session1", task_id="task1", port=0)))
    assert response[-1].done == True
    result_data = b""
    for subresponse in response:
        result_data = result_data + subresponse.data

    assert result_data == b"Hello World"


def test_forwarding():
    # Forwarding data from one node to the next one
    test_address = "127.0.0.1:18502"
    test_address2 = "127.0.0.1:18503"
    handler = DummyHandler()
    handler2 = DummyHandler()

    server = Server(handler)
    server2 = Server(handler2)

    server.start(test_address)
    server2.start(test_address2)

    channel = grpc.insecure_channel(test_address)
    stub = MnpServiceStub(channel)

    channel2 = grpc.insecure_channel(test_address2)
    stub2 = MnpServiceStub(channel2)

    port_config = PortConfiguration(
        [InputPortConfiguration("content1")],
        [OutputPortConfiguration("content2", "mnp://{}/session2/0".format(test_address2))]
    )

    response = list(stub.Init(InitRequest(
        session_id="session1",
        inputs=port_config.inputs_as_proto(),
        outputs=port_config.outputs_as_proto()
    )))
    # Last result must be ready
    assert response[-1].state == SS_READY

    port_config2 = PortConfiguration(
        [InputPortConfiguration("content2")],
        [OutputPortConfiguration("content3")]
    )

    response2 = list(stub2.Init(InitRequest(
        session_id="session2",
        inputs=port_config2.inputs_as_proto(),
        outputs=port_config2.outputs_as_proto()
    )))

    # Last result must be ready
    assert response2[-1].state == SS_READY

    input_data = [
        PushRequest(session_id="session1", task_id="task1", port=0),
        PushRequest(data=b"Hello you "),
        PushRequest(data=b"will be "),
        PushRequest(data=b"forwarded!"),
        PushRequest(done=True)
    ]

    stub.Push(iter(input_data))

    response = list(stub2.Pull(PullRequest(session_id="session2", task_id="task1", port=0)))
    assert response[-1].done == True
    result_data = b""
    for subresponse in response:
        result_data = result_data + subresponse.data

    assert result_data == b"Hello you will be forwarded!"

    channel.close()
    channel2.close()

    server.stop()
    server2.stop()
