# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: mantik/engine/engine.proto
"""Generated protocol buffer code."""
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from google.protobuf import empty_pb2 as google_dot_protobuf_dot_empty__pb2


DESCRIPTOR = _descriptor.FileDescriptor(
  name='mantik/engine/engine.proto',
  package='ai.mantik.engine.protos',
  syntax='proto3',
  serialized_options=b'Z\rmantik/engine',
  create_key=_descriptor._internal_create_key,
  serialized_pb=b'\n\x1amantik/engine/engine.proto\x12\x17\x61i.mantik.engine.protos\x1a\x1bgoogle/protobuf/empty.proto\"\"\n\x0fVersionResponse\x12\x0f\n\x07version\x18\x01 \x01(\t2]\n\x0c\x41\x62outService\x12M\n\x07Version\x12\x16.google.protobuf.Empty\x1a(.ai.mantik.engine.protos.VersionResponse\"\x00\x42\x0fZ\rmantik/engineb\x06proto3'
  ,
  dependencies=[google_dot_protobuf_dot_empty__pb2.DESCRIPTOR,])




_VERSIONRESPONSE = _descriptor.Descriptor(
  name='VersionResponse',
  full_name='ai.mantik.engine.protos.VersionResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  create_key=_descriptor._internal_create_key,
  fields=[
    _descriptor.FieldDescriptor(
      name='version', full_name='ai.mantik.engine.protos.VersionResponse.version', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=b"".decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR,  create_key=_descriptor._internal_create_key),
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
  serialized_start=84,
  serialized_end=118,
)

DESCRIPTOR.message_types_by_name['VersionResponse'] = _VERSIONRESPONSE
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

VersionResponse = _reflection.GeneratedProtocolMessageType('VersionResponse', (_message.Message,), {
  'DESCRIPTOR' : _VERSIONRESPONSE,
  '__module__' : 'mantik.engine.engine_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.VersionResponse)
  })
_sym_db.RegisterMessage(VersionResponse)


DESCRIPTOR._options = None

_ABOUTSERVICE = _descriptor.ServiceDescriptor(
  name='AboutService',
  full_name='ai.mantik.engine.protos.AboutService',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  create_key=_descriptor._internal_create_key,
  serialized_start=120,
  serialized_end=213,
  methods=[
  _descriptor.MethodDescriptor(
    name='Version',
    full_name='ai.mantik.engine.protos.AboutService.Version',
    index=0,
    containing_service=None,
    input_type=google_dot_protobuf_dot_empty__pb2._EMPTY,
    output_type=_VERSIONRESPONSE,
    serialized_options=None,
    create_key=_descriptor._internal_create_key,
  ),
])
_sym_db.RegisterServiceDescriptor(_ABOUTSERVICE)

DESCRIPTOR.services_by_name['AboutService'] = _ABOUTSERVICE

# @@protoc_insertion_point(module_scope)