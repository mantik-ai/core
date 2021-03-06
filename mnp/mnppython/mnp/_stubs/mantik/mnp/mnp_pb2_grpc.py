# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
"""Client and server classes corresponding to protobuf-defined services."""
import grpc

from google.protobuf import empty_pb2 as google_dot_protobuf_dot_empty__pb2
from . import mnp_pb2 as mantik_dot_mnp_dot_mnp__pb2


class MnpServiceStub(object):
    """Mantik Node Protocol
    Underlying protocol for communication between Network nodes
    Design Goal: Free of mantik specific terms. Just solving the
    "Each node can execute tasks and forward output to the next problem"
    """

    def __init__(self, channel):
        """Constructor.

        Args:
            channel: A grpc.Channel.
        """
        self.About = channel.unary_unary(
                '/MnpService/About',
                request_serializer=google_dot_protobuf_dot_empty__pb2.Empty.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.AboutResponse.FromString,
                )
        self.Init = channel.unary_stream(
                '/MnpService/Init',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.InitRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.InitResponse.FromString,
                )
        self.Quit = channel.unary_unary(
                '/MnpService/Quit',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.QuitRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.QuitResponse.FromString,
                )
        self.QuitSession = channel.unary_unary(
                '/MnpService/QuitSession',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.QuitSessionRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.QuitSessionResponse.FromString,
                )
        self.Push = channel.stream_unary(
                '/MnpService/Push',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.PushRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.PushResponse.FromString,
                )
        self.Pull = channel.unary_stream(
                '/MnpService/Pull',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.PullRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.PullResponse.FromString,
                )
        self.QueryTask = channel.unary_unary(
                '/MnpService/QueryTask',
                request_serializer=mantik_dot_mnp_dot_mnp__pb2.QueryTaskRequest.SerializeToString,
                response_deserializer=mantik_dot_mnp_dot_mnp__pb2.QueryTaskResponse.FromString,
                )


class MnpServiceServicer(object):
    """Mantik Node Protocol
    Underlying protocol for communication between Network nodes
    Design Goal: Free of mantik specific terms. Just solving the
    "Each node can execute tasks and forward output to the next problem"
    """

    def About(self, request, context):
        """Gathers information about the bridge.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def Init(self, request, context):
        """Initialize the Node
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def Quit(self, request, context):
        """Quit the node.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def QuitSession(self, request, context):
        """Quits the session
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def Push(self, request_iterator, context):
        """Push data into a port, creating a task when necessary
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def Pull(self, request, context):
        """Pull data from a port, creating a task when necesary
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def QueryTask(self, request, context):
        """Queries a task (can create if not existent)
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_MnpServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
            'About': grpc.unary_unary_rpc_method_handler(
                    servicer.About,
                    request_deserializer=google_dot_protobuf_dot_empty__pb2.Empty.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.AboutResponse.SerializeToString,
            ),
            'Init': grpc.unary_stream_rpc_method_handler(
                    servicer.Init,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.InitRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.InitResponse.SerializeToString,
            ),
            'Quit': grpc.unary_unary_rpc_method_handler(
                    servicer.Quit,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.QuitRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.QuitResponse.SerializeToString,
            ),
            'QuitSession': grpc.unary_unary_rpc_method_handler(
                    servicer.QuitSession,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.QuitSessionRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.QuitSessionResponse.SerializeToString,
            ),
            'Push': grpc.stream_unary_rpc_method_handler(
                    servicer.Push,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.PushRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.PushResponse.SerializeToString,
            ),
            'Pull': grpc.unary_stream_rpc_method_handler(
                    servicer.Pull,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.PullRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.PullResponse.SerializeToString,
            ),
            'QueryTask': grpc.unary_unary_rpc_method_handler(
                    servicer.QueryTask,
                    request_deserializer=mantik_dot_mnp_dot_mnp__pb2.QueryTaskRequest.FromString,
                    response_serializer=mantik_dot_mnp_dot_mnp__pb2.QueryTaskResponse.SerializeToString,
            ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
            'MnpService', rpc_method_handlers)
    server.add_generic_rpc_handlers((generic_handler,))


 # This class is part of an EXPERIMENTAL API.
class MnpService(object):
    """Mantik Node Protocol
    Underlying protocol for communication between Network nodes
    Design Goal: Free of mantik specific terms. Just solving the
    "Each node can execute tasks and forward output to the next problem"
    """

    @staticmethod
    def About(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(request, target, '/MnpService/About',
            google_dot_protobuf_dot_empty__pb2.Empty.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.AboutResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def Init(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_stream(request, target, '/MnpService/Init',
            mantik_dot_mnp_dot_mnp__pb2.InitRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.InitResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def Quit(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(request, target, '/MnpService/Quit',
            mantik_dot_mnp_dot_mnp__pb2.QuitRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.QuitResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def QuitSession(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(request, target, '/MnpService/QuitSession',
            mantik_dot_mnp_dot_mnp__pb2.QuitSessionRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.QuitSessionResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def Push(request_iterator,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.stream_unary(request_iterator, target, '/MnpService/Push',
            mantik_dot_mnp_dot_mnp__pb2.PushRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.PushResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def Pull(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_stream(request, target, '/MnpService/Pull',
            mantik_dot_mnp_dot_mnp__pb2.PullRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.PullResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)

    @staticmethod
    def QueryTask(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(request, target, '/MnpService/QueryTask',
            mantik_dot_mnp_dot_mnp__pb2.QueryTaskRequest.SerializeToString,
            mantik_dot_mnp_dot_mnp__pb2.QueryTaskResponse.FromString,
            options, channel_credentials,
            insecure, call_credentials, compression, wait_for_ready, timeout, metadata)
