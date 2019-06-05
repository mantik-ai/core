# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: mantik/protobuf/engine/sessions.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()




DESCRIPTOR = _descriptor.FileDescriptor(
  name='mantik/protobuf/engine/sessions.proto',
  package='ai.mantik.engine.protos',
  syntax='proto3',
  serialized_options=None,
  serialized_pb=_b('\n%mantik/protobuf/engine/sessions.proto\x12\x17\x61i.mantik.engine.protos\"\x16\n\x14\x43reateSessionRequest\"+\n\x15\x43reateSessionResponse\x12\x12\n\nsession_id\x18\x01 \x01(\t\")\n\x13\x43loseSessionRequest\x12\x12\n\nsession_id\x18\x01 \x01(\t\"\x16\n\x14\x43loseSessionResponse2\xf1\x01\n\x0eSessionService\x12p\n\rCreateSession\x12-.ai.mantik.engine.protos.CreateSessionRequest\x1a..ai.mantik.engine.protos.CreateSessionResponse\"\x00\x12m\n\x0c\x43loseSession\x12,.ai.mantik.engine.protos.CloseSessionRequest\x1a-.ai.mantik.engine.protos.CloseSessionResponse\"\x00\x62\x06proto3')
)




_CREATESESSIONREQUEST = _descriptor.Descriptor(
  name='CreateSessionRequest',
  full_name='ai.mantik.engine.protos.CreateSessionRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=66,
  serialized_end=88,
)


_CREATESESSIONRESPONSE = _descriptor.Descriptor(
  name='CreateSessionResponse',
  full_name='ai.mantik.engine.protos.CreateSessionResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='session_id', full_name='ai.mantik.engine.protos.CreateSessionResponse.session_id', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=90,
  serialized_end=133,
)


_CLOSESESSIONREQUEST = _descriptor.Descriptor(
  name='CloseSessionRequest',
  full_name='ai.mantik.engine.protos.CloseSessionRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='session_id', full_name='ai.mantik.engine.protos.CloseSessionRequest.session_id', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=135,
  serialized_end=176,
)


_CLOSESESSIONRESPONSE = _descriptor.Descriptor(
  name='CloseSessionResponse',
  full_name='ai.mantik.engine.protos.CloseSessionResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
  ],
  extensions=[
  ],
  nested_types=[],
  enum_types=[
  ],
  serialized_options=None,
  is_extendable=False,
  syntax='proto3',
  extension_ranges=[],
  oneofs=[
  ],
  serialized_start=178,
  serialized_end=200,
)

DESCRIPTOR.message_types_by_name['CreateSessionRequest'] = _CREATESESSIONREQUEST
DESCRIPTOR.message_types_by_name['CreateSessionResponse'] = _CREATESESSIONRESPONSE
DESCRIPTOR.message_types_by_name['CloseSessionRequest'] = _CLOSESESSIONREQUEST
DESCRIPTOR.message_types_by_name['CloseSessionResponse'] = _CLOSESESSIONRESPONSE
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

CreateSessionRequest = _reflection.GeneratedProtocolMessageType('CreateSessionRequest', (_message.Message,), dict(
  DESCRIPTOR = _CREATESESSIONREQUEST,
  __module__ = 'mantik.protobuf.engine.sessions_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.CreateSessionRequest)
  ))
_sym_db.RegisterMessage(CreateSessionRequest)

CreateSessionResponse = _reflection.GeneratedProtocolMessageType('CreateSessionResponse', (_message.Message,), dict(
  DESCRIPTOR = _CREATESESSIONRESPONSE,
  __module__ = 'mantik.protobuf.engine.sessions_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.CreateSessionResponse)
  ))
_sym_db.RegisterMessage(CreateSessionResponse)

CloseSessionRequest = _reflection.GeneratedProtocolMessageType('CloseSessionRequest', (_message.Message,), dict(
  DESCRIPTOR = _CLOSESESSIONREQUEST,
  __module__ = 'mantik.protobuf.engine.sessions_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.CloseSessionRequest)
  ))
_sym_db.RegisterMessage(CloseSessionRequest)

CloseSessionResponse = _reflection.GeneratedProtocolMessageType('CloseSessionResponse', (_message.Message,), dict(
  DESCRIPTOR = _CLOSESESSIONRESPONSE,
  __module__ = 'mantik.protobuf.engine.sessions_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.CloseSessionResponse)
  ))
_sym_db.RegisterMessage(CloseSessionResponse)



_SESSIONSERVICE = _descriptor.ServiceDescriptor(
  name='SessionService',
  full_name='ai.mantik.engine.protos.SessionService',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  serialized_start=203,
  serialized_end=444,
  methods=[
  _descriptor.MethodDescriptor(
    name='CreateSession',
    full_name='ai.mantik.engine.protos.SessionService.CreateSession',
    index=0,
    containing_service=None,
    input_type=_CREATESESSIONREQUEST,
    output_type=_CREATESESSIONRESPONSE,
    serialized_options=None,
  ),
  _descriptor.MethodDescriptor(
    name='CloseSession',
    full_name='ai.mantik.engine.protos.SessionService.CloseSession',
    index=1,
    containing_service=None,
    input_type=_CLOSESESSIONREQUEST,
    output_type=_CLOSESESSIONRESPONSE,
    serialized_options=None,
  ),
])
_sym_db.RegisterServiceDescriptor(_SESSIONSERVICE)

DESCRIPTOR.services_by_name['SessionService'] = _SESSIONSERVICE

# @@protoc_insertion_point(module_scope)
