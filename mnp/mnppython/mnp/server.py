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

import grpc
from concurrent import futures
from mnp import ServiceServer, Handler
from mnp._stubs.mantik.mnp.mnp_pb2_grpc import add_MnpServiceServicer_to_server


class Server:

    def __init__(self, handler: Handler, tpe: futures.ThreadPoolExecutor = None):
        if tpe is None:
            tpe = futures.ThreadPoolExecutor(max_workers=10)
        self.handler = handler
        self.service = ServiceServer(handler, tpe)
        self.gprc_server = grpc.server(tpe)
        add_MnpServiceServicer_to_server(self.service, self.gprc_server)

    def start(self, address: str):
        self.gprc_server.add_insecure_port(address)
        self.gprc_server.start()

    def wait_for_termination(self):
        self.gprc_server.wait_for_termination()

    def stop(self, grace=None):
        self.gprc_server.stop(grace)
