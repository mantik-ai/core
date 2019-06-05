# -*- coding: utf-8 -*-
# Generated by the protocol buffer compiler.  DO NOT EDIT!
# source: mantik/protobuf/engine/graph_executor.proto

import sys
_b=sys.version_info[0]<3 and (lambda x:x) or (lambda x:x.encode('latin1'))
from google.protobuf import descriptor as _descriptor
from google.protobuf import message as _message
from google.protobuf import reflection as _reflection
from google.protobuf import symbol_database as _symbol_database
# @@protoc_insertion_point(imports)

_sym_db = _symbol_database.Default()


from mantik.protobuf.engine import ds_pb2 as mantik_dot_protobuf_dot_engine_dot_ds__pb2


DESCRIPTOR = _descriptor.FileDescriptor(
  name='mantik/protobuf/engine/graph_executor.proto',
  package='ai.mantik.engine.protos',
  syntax='proto3',
  serialized_options=None,
  serialized_pb=_b('\n+mantik/protobuf/engine/graph_executor.proto\x12\x17\x61i.mantik.engine.protos\x1a\x1fmantik/protobuf/engine/ds.proto\"u\n\x10\x46\x65tchItemRequest\x12\x12\n\nsession_id\x18\x01 \x01(\t\x12\x12\n\ndataset_id\x18\x02 \x01(\t\x12\x39\n\x08\x65ncoding\x18\x03 \x01(\x0e\x32\'.ai.mantik.engine.protos.BundleEncoding\"D\n\x11\x46\x65tchItemResponse\x12/\n\x06\x62undle\x18\x01 \x01(\x0b\x32\x1f.ai.mantik.engine.protos.Bundle\"D\n\x0fSaveItemRequest\x12\x12\n\nsession_id\x18\x01 \x01(\t\x12\x0f\n\x07item_id\x18\x02 \x01(\t\x12\x0c\n\x04name\x18\x03 \x01(\t\" \n\x10SaveItemResponse\x12\x0c\n\x04name\x18\x01 \x01(\t2\xe2\x01\n\x14GraphExecutorService\x12g\n\x0c\x46\x65tchDataSet\x12).ai.mantik.engine.protos.FetchItemRequest\x1a*.ai.mantik.engine.protos.FetchItemResponse\"\x00\x12\x61\n\x08SaveItem\x12(.ai.mantik.engine.protos.SaveItemRequest\x1a).ai.mantik.engine.protos.SaveItemResponse\"\x00\x62\x06proto3')
  ,
  dependencies=[mantik_dot_protobuf_dot_engine_dot_ds__pb2.DESCRIPTOR,])




_FETCHITEMREQUEST = _descriptor.Descriptor(
  name='FetchItemRequest',
  full_name='ai.mantik.engine.protos.FetchItemRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='session_id', full_name='ai.mantik.engine.protos.FetchItemRequest.session_id', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='dataset_id', full_name='ai.mantik.engine.protos.FetchItemRequest.dataset_id', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='encoding', full_name='ai.mantik.engine.protos.FetchItemRequest.encoding', index=2,
      number=3, type=14, cpp_type=8, label=1,
      has_default_value=False, default_value=0,
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
  serialized_start=105,
  serialized_end=222,
)


_FETCHITEMRESPONSE = _descriptor.Descriptor(
  name='FetchItemResponse',
  full_name='ai.mantik.engine.protos.FetchItemResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='bundle', full_name='ai.mantik.engine.protos.FetchItemResponse.bundle', index=0,
      number=1, type=11, cpp_type=10, label=1,
      has_default_value=False, default_value=None,
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
  serialized_start=224,
  serialized_end=292,
)


_SAVEITEMREQUEST = _descriptor.Descriptor(
  name='SaveItemRequest',
  full_name='ai.mantik.engine.protos.SaveItemRequest',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='session_id', full_name='ai.mantik.engine.protos.SaveItemRequest.session_id', index=0,
      number=1, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='item_id', full_name='ai.mantik.engine.protos.SaveItemRequest.item_id', index=1,
      number=2, type=9, cpp_type=9, label=1,
      has_default_value=False, default_value=_b("").decode('utf-8'),
      message_type=None, enum_type=None, containing_type=None,
      is_extension=False, extension_scope=None,
      serialized_options=None, file=DESCRIPTOR),
    _descriptor.FieldDescriptor(
      name='name', full_name='ai.mantik.engine.protos.SaveItemRequest.name', index=2,
      number=3, type=9, cpp_type=9, label=1,
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
  serialized_start=294,
  serialized_end=362,
)


_SAVEITEMRESPONSE = _descriptor.Descriptor(
  name='SaveItemResponse',
  full_name='ai.mantik.engine.protos.SaveItemResponse',
  filename=None,
  file=DESCRIPTOR,
  containing_type=None,
  fields=[
    _descriptor.FieldDescriptor(
      name='name', full_name='ai.mantik.engine.protos.SaveItemResponse.name', index=0,
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
  serialized_start=364,
  serialized_end=396,
)

_FETCHITEMREQUEST.fields_by_name['encoding'].enum_type = mantik_dot_protobuf_dot_engine_dot_ds__pb2._BUNDLEENCODING
_FETCHITEMRESPONSE.fields_by_name['bundle'].message_type = mantik_dot_protobuf_dot_engine_dot_ds__pb2._BUNDLE
DESCRIPTOR.message_types_by_name['FetchItemRequest'] = _FETCHITEMREQUEST
DESCRIPTOR.message_types_by_name['FetchItemResponse'] = _FETCHITEMRESPONSE
DESCRIPTOR.message_types_by_name['SaveItemRequest'] = _SAVEITEMREQUEST
DESCRIPTOR.message_types_by_name['SaveItemResponse'] = _SAVEITEMRESPONSE
_sym_db.RegisterFileDescriptor(DESCRIPTOR)

FetchItemRequest = _reflection.GeneratedProtocolMessageType('FetchItemRequest', (_message.Message,), dict(
  DESCRIPTOR = _FETCHITEMREQUEST,
  __module__ = 'mantik.protobuf.engine.graph_executor_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.FetchItemRequest)
  ))
_sym_db.RegisterMessage(FetchItemRequest)

FetchItemResponse = _reflection.GeneratedProtocolMessageType('FetchItemResponse', (_message.Message,), dict(
  DESCRIPTOR = _FETCHITEMRESPONSE,
  __module__ = 'mantik.protobuf.engine.graph_executor_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.FetchItemResponse)
  ))
_sym_db.RegisterMessage(FetchItemResponse)

SaveItemRequest = _reflection.GeneratedProtocolMessageType('SaveItemRequest', (_message.Message,), dict(
  DESCRIPTOR = _SAVEITEMREQUEST,
  __module__ = 'mantik.protobuf.engine.graph_executor_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.SaveItemRequest)
  ))
_sym_db.RegisterMessage(SaveItemRequest)

SaveItemResponse = _reflection.GeneratedProtocolMessageType('SaveItemResponse', (_message.Message,), dict(
  DESCRIPTOR = _SAVEITEMRESPONSE,
  __module__ = 'mantik.protobuf.engine.graph_executor_pb2'
  # @@protoc_insertion_point(class_scope:ai.mantik.engine.protos.SaveItemResponse)
  ))
_sym_db.RegisterMessage(SaveItemResponse)



_GRAPHEXECUTORSERVICE = _descriptor.ServiceDescriptor(
  name='GraphExecutorService',
  full_name='ai.mantik.engine.protos.GraphExecutorService',
  file=DESCRIPTOR,
  index=0,
  serialized_options=None,
  serialized_start=399,
  serialized_end=625,
  methods=[
  _descriptor.MethodDescriptor(
    name='FetchDataSet',
    full_name='ai.mantik.engine.protos.GraphExecutorService.FetchDataSet',
    index=0,
    containing_service=None,
    input_type=_FETCHITEMREQUEST,
    output_type=_FETCHITEMRESPONSE,
    serialized_options=None,
  ),
  _descriptor.MethodDescriptor(
    name='SaveItem',
    full_name='ai.mantik.engine.protos.GraphExecutorService.SaveItem',
    index=1,
    containing_service=None,
    input_type=_SAVEITEMREQUEST,
    output_type=_SAVEITEMRESPONSE,
    serialized_options=None,
  ),
])
_sym_db.RegisterServiceDescriptor(_GRAPHEXECUTORSERVICE)

DESCRIPTOR.services_by_name['GraphExecutorService'] = _GRAPHEXECUTORSERVICE

# @@protoc_insertion_point(module_scope)
