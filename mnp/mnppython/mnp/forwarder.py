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

from typing import BinaryIO, Iterable
from mnp.mnp_url import MnpUrl
from mnp._stubs.mantik.mnp.mnp_pb2_grpc import MnpServiceStub
from mnp._stubs.mantik.mnp.mnp_pb2 import PushRequest

import grpc
from io import DEFAULT_BUFFER_SIZE


class Forwarder:
    """
    Simple thread based forwarding routine which takes some input and forwards it
    to a nother MNP Node
    """

    def __init__(self, task_id: str, binary_input: BinaryIO, destination_url: str):
        self.binary_input = binary_input
        self.task_id = task_id
        self.destination = MnpUrl.parse(destination_url)

    def run(self):
        channel = grpc.insecure_channel(self.destination.address)
        try:
            self.run_with_channel(channel)
        finally:
            channel.close()

    def run_with_channel(self, channel: grpc.Channel):
        stub = MnpServiceStub(channel)

        def creator() -> Iterable[PushRequest]:
            yield PushRequest(
                session_id=self.destination.session_id,
                task_id=self.task_id,
                port=self.destination.port
            )
            data = self.binary_input.read(DEFAULT_BUFFER_SIZE)
            while data:
                yield PushRequest(data=data)
                data = self.binary_input.read(DEFAULT_BUFFER_SIZE)
            yield PushRequest(
                done=True
            )

        stub.Push(creator())
