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

from mnp import Server
from tests.dummy_handler import DummyHandler
import grpc
from mnp._stubs.mantik.mnp.mnp_pb2_grpc import MnpServiceStub
from mnp._stubs.mantik.mnp.mnp_pb2 import *
from mnp import PortConfiguration, InputPortConfiguration, OutputPortConfiguration
import pytest

test_port = 18501
test_address = "127.0.0.1:18501"


@pytest.fixture
def dummy_server_handler():
    handler = DummyHandler()
    server = Server(handler)
    server.start(test_address)
    channel = grpc.insecure_channel(test_address)
    stub = MnpServiceStub(channel)
    yield stub, handler
    channel.close()
    server.stop()


def test_single_about(dummy_server_handler):
    stub, handler = dummy_server_handler

    response = stub.About(AboutRequest())
    assert response.name == "Dummy Handler"

    assert not handler.quit_called
    stub.Quit(QuitRequest())
    assert handler.quit_called


def test_single_session_start(dummy_server_handler):
    stub, handler = dummy_server_handler

    port_config = PortConfiguration(
        [InputPortConfiguration("content1")], [OutputPortConfiguration("content2")]
    )

    response = stub.Init(InitRequest(
        session_id="session1",
        inputs=port_config.inputs_as_proto(),
        outputs=port_config.outputs_as_proto()
    ))

    response_list = list(response)
    assert [
               response_list[0].state,
               response_list[1].state,
               response_list[2].state
           ] == [SS_INITIALIZING, SS_DOWNLOADING, SS_READY]

    assert len(handler.sessions) == 1
    session1 = handler.get_session(0)
    assert session1.session_id == "session1"
    assert session1.ports == port_config

    assert session1.quit_requested == False
    stub.QuitSession(QuitSessionRequest(session_id="session1"))

    assert session1.quit_requested == True
