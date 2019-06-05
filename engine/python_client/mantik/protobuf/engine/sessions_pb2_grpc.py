# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
import grpc

from mantik.protobuf.engine import sessions_pb2 as mantik_dot_protobuf_dot_engine_dot_sessions__pb2


class SessionServiceStub(object):
  """* Service for session handling. 
  """

  def __init__(self, channel):
    """Constructor.

    Args:
      channel: A grpc.Channel.
    """
    self.CreateSession = channel.unary_unary(
        '/ai.mantik.engine.protos.SessionService/CreateSession',
        request_serializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CreateSessionRequest.SerializeToString,
        response_deserializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CreateSessionResponse.FromString,
        )
    self.CloseSession = channel.unary_unary(
        '/ai.mantik.engine.protos.SessionService/CloseSession',
        request_serializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CloseSessionRequest.SerializeToString,
        response_deserializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CloseSessionResponse.FromString,
        )


class SessionServiceServicer(object):
  """* Service for session handling. 
  """

  def CreateSession(self, request, context):
    # missing associated documentation comment in .proto file
    pass
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')

  def CloseSession(self, request, context):
    # missing associated documentation comment in .proto file
    pass
    context.set_code(grpc.StatusCode.UNIMPLEMENTED)
    context.set_details('Method not implemented!')
    raise NotImplementedError('Method not implemented!')


def add_SessionServiceServicer_to_server(servicer, server):
  rpc_method_handlers = {
      'CreateSession': grpc.unary_unary_rpc_method_handler(
          servicer.CreateSession,
          request_deserializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CreateSessionRequest.FromString,
          response_serializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CreateSessionResponse.SerializeToString,
      ),
      'CloseSession': grpc.unary_unary_rpc_method_handler(
          servicer.CloseSession,
          request_deserializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CloseSessionRequest.FromString,
          response_serializer=mantik_dot_protobuf_dot_engine_dot_sessions__pb2.CloseSessionResponse.SerializeToString,
      ),
  }
  generic_handler = grpc.method_handlers_generic_handler(
      'ai.mantik.engine.protos.SessionService', rpc_method_handlers)
  server.add_generic_rpc_handlers((generic_handler,))
