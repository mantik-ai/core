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
