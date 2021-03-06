// Code generated by protoc-gen-go. DO NOT EDIT.
// versions:
// 	protoc-gen-go v1.26.0
// 	protoc        v3.6.1
// source: mantik/engine/graph_executor.proto

package engine

import (
	context "context"
	grpc "google.golang.org/grpc"
	codes "google.golang.org/grpc/codes"
	status "google.golang.org/grpc/status"
	protoreflect "google.golang.org/protobuf/reflect/protoreflect"
	protoimpl "google.golang.org/protobuf/runtime/protoimpl"
	reflect "reflect"
	sync "sync"
)

const (
	// Verify that this generated code is sufficiently up-to-date.
	_ = protoimpl.EnforceVersion(20 - protoimpl.MinVersion)
	// Verify that runtime/protoimpl is sufficiently up-to-date.
	_ = protoimpl.EnforceVersion(protoimpl.MaxVersion - 20)
)

type FetchItemRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	SessionId string `protobuf:"bytes,1,opt,name=session_id,json=sessionId,proto3" json:"session_id,omitempty"`
	DatasetId string `protobuf:"bytes,2,opt,name=dataset_id,json=datasetId,proto3" json:"dataset_id,omitempty"`
	// Requested Encoding
	Encoding BundleEncoding `protobuf:"varint,3,opt,name=encoding,proto3,enum=ai.mantik.engine.protos.BundleEncoding" json:"encoding,omitempty"`
	// Meta Information about that request
	Meta *ActionMeta `protobuf:"bytes,4,opt,name=meta,proto3" json:"meta,omitempty"`
}

func (x *FetchItemRequest) Reset() {
	*x = FetchItemRequest{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[0]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *FetchItemRequest) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*FetchItemRequest) ProtoMessage() {}

func (x *FetchItemRequest) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[0]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use FetchItemRequest.ProtoReflect.Descriptor instead.
func (*FetchItemRequest) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{0}
}

func (x *FetchItemRequest) GetSessionId() string {
	if x != nil {
		return x.SessionId
	}
	return ""
}

func (x *FetchItemRequest) GetDatasetId() string {
	if x != nil {
		return x.DatasetId
	}
	return ""
}

func (x *FetchItemRequest) GetEncoding() BundleEncoding {
	if x != nil {
		return x.Encoding
	}
	return BundleEncoding_ENCODING_UNKNOWN
}

func (x *FetchItemRequest) GetMeta() *ActionMeta {
	if x != nil {
		return x.Meta
	}
	return nil
}

type FetchItemResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	Bundle *Bundle `protobuf:"bytes,1,opt,name=bundle,proto3" json:"bundle,omitempty"`
}

func (x *FetchItemResponse) Reset() {
	*x = FetchItemResponse{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[1]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *FetchItemResponse) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*FetchItemResponse) ProtoMessage() {}

func (x *FetchItemResponse) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[1]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use FetchItemResponse.ProtoReflect.Descriptor instead.
func (*FetchItemResponse) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{1}
}

func (x *FetchItemResponse) GetBundle() *Bundle {
	if x != nil {
		return x.Bundle
	}
	return nil
}

type SaveItemRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	SessionId string `protobuf:"bytes,1,opt,name=session_id,json=sessionId,proto3" json:"session_id,omitempty"`
	ItemId    string `protobuf:"bytes,2,opt,name=item_id,json=itemId,proto3" json:"item_id,omitempty"`
	// Name under which the item is to be stored (can be name:version)
	// if not given, the current name is used (see tag command)
	Name string `protobuf:"bytes,3,opt,name=name,proto3" json:"name,omitempty"`
	// Meta Information about that request
	Meta *ActionMeta `protobuf:"bytes,4,opt,name=meta,proto3" json:"meta,omitempty"`
}

func (x *SaveItemRequest) Reset() {
	*x = SaveItemRequest{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[2]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *SaveItemRequest) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*SaveItemRequest) ProtoMessage() {}

func (x *SaveItemRequest) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[2]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use SaveItemRequest.ProtoReflect.Descriptor instead.
func (*SaveItemRequest) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{2}
}

func (x *SaveItemRequest) GetSessionId() string {
	if x != nil {
		return x.SessionId
	}
	return ""
}

func (x *SaveItemRequest) GetItemId() string {
	if x != nil {
		return x.ItemId
	}
	return ""
}

func (x *SaveItemRequest) GetName() string {
	if x != nil {
		return x.Name
	}
	return ""
}

func (x *SaveItemRequest) GetMeta() *ActionMeta {
	if x != nil {
		return x.Meta
	}
	return nil
}

type SaveItemResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	// Name under which the item has been saved, can be empty
	Name string `protobuf:"bytes,1,opt,name=name,proto3" json:"name,omitempty"`
	// Mantik Item Id under which this item has been saved.
	MantikItemId string `protobuf:"bytes,2,opt,name=mantik_item_id,json=mantikItemId,proto3" json:"mantik_item_id,omitempty"`
	// Meta Information about that request
	Meta *ActionMeta `protobuf:"bytes,3,opt,name=meta,proto3" json:"meta,omitempty"`
}

func (x *SaveItemResponse) Reset() {
	*x = SaveItemResponse{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[3]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *SaveItemResponse) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*SaveItemResponse) ProtoMessage() {}

func (x *SaveItemResponse) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[3]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use SaveItemResponse.ProtoReflect.Descriptor instead.
func (*SaveItemResponse) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{3}
}

func (x *SaveItemResponse) GetName() string {
	if x != nil {
		return x.Name
	}
	return ""
}

func (x *SaveItemResponse) GetMantikItemId() string {
	if x != nil {
		return x.MantikItemId
	}
	return ""
}

func (x *SaveItemResponse) GetMeta() *ActionMeta {
	if x != nil {
		return x.Meta
	}
	return nil
}

type DeployItemRequest struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	SessionId string `protobuf:"bytes,1,opt,name=session_id,json=sessionId,proto3" json:"session_id,omitempty"`
	ItemId    string `protobuf:"bytes,2,opt,name=item_id,json=itemId,proto3" json:"item_id,omitempty"`
	// Ingress name for the item (required for Pipelines, also only supported there yet)
	IngressName string `protobuf:"bytes,3,opt,name=ingress_name,json=ingressName,proto3" json:"ingress_name,omitempty"`
	// A Hint for the Service Name (not required)
	NameHint string `protobuf:"bytes,4,opt,name=name_hint,json=nameHint,proto3" json:"name_hint,omitempty"`
	// Meta Information about that request
	Meta *ActionMeta `protobuf:"bytes,5,opt,name=meta,proto3" json:"meta,omitempty"`
}

func (x *DeployItemRequest) Reset() {
	*x = DeployItemRequest{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[4]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *DeployItemRequest) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*DeployItemRequest) ProtoMessage() {}

func (x *DeployItemRequest) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[4]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use DeployItemRequest.ProtoReflect.Descriptor instead.
func (*DeployItemRequest) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{4}
}

func (x *DeployItemRequest) GetSessionId() string {
	if x != nil {
		return x.SessionId
	}
	return ""
}

func (x *DeployItemRequest) GetItemId() string {
	if x != nil {
		return x.ItemId
	}
	return ""
}

func (x *DeployItemRequest) GetIngressName() string {
	if x != nil {
		return x.IngressName
	}
	return ""
}

func (x *DeployItemRequest) GetNameHint() string {
	if x != nil {
		return x.NameHint
	}
	return ""
}

func (x *DeployItemRequest) GetMeta() *ActionMeta {
	if x != nil {
		return x.Meta
	}
	return nil
}

type DeployItemResponse struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	// Service name used in the end
	Name string `protobuf:"bytes,1,opt,name=name,proto3" json:"name,omitempty"`
	// Internal URL (inside the Cluster)
	InternalUrl string `protobuf:"bytes,2,opt,name=internal_url,json=internalUrl,proto3" json:"internal_url,omitempty"`
	// External URL (only valid if an ingress is set)
	ExternalUrl string `protobuf:"bytes,3,opt,name=external_url,json=externalUrl,proto3" json:"external_url,omitempty"`
}

func (x *DeployItemResponse) Reset() {
	*x = DeployItemResponse{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[5]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *DeployItemResponse) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*DeployItemResponse) ProtoMessage() {}

func (x *DeployItemResponse) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[5]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use DeployItemResponse.ProtoReflect.Descriptor instead.
func (*DeployItemResponse) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{5}
}

func (x *DeployItemResponse) GetName() string {
	if x != nil {
		return x.Name
	}
	return ""
}

func (x *DeployItemResponse) GetInternalUrl() string {
	if x != nil {
		return x.InternalUrl
	}
	return ""
}

func (x *DeployItemResponse) GetExternalUrl() string {
	if x != nil {
		return x.ExternalUrl
	}
	return ""
}

// Meta Information about an action
type ActionMeta struct {
	state         protoimpl.MessageState
	sizeCache     protoimpl.SizeCache
	unknownFields protoimpl.UnknownFields

	// Optional name
	Name string `protobuf:"bytes,1,opt,name=name,proto3" json:"name,omitempty"`
}

func (x *ActionMeta) Reset() {
	*x = ActionMeta{}
	if protoimpl.UnsafeEnabled {
		mi := &file_mantik_engine_graph_executor_proto_msgTypes[6]
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		ms.StoreMessageInfo(mi)
	}
}

func (x *ActionMeta) String() string {
	return protoimpl.X.MessageStringOf(x)
}

func (*ActionMeta) ProtoMessage() {}

func (x *ActionMeta) ProtoReflect() protoreflect.Message {
	mi := &file_mantik_engine_graph_executor_proto_msgTypes[6]
	if protoimpl.UnsafeEnabled && x != nil {
		ms := protoimpl.X.MessageStateOf(protoimpl.Pointer(x))
		if ms.LoadMessageInfo() == nil {
			ms.StoreMessageInfo(mi)
		}
		return ms
	}
	return mi.MessageOf(x)
}

// Deprecated: Use ActionMeta.ProtoReflect.Descriptor instead.
func (*ActionMeta) Descriptor() ([]byte, []int) {
	return file_mantik_engine_graph_executor_proto_rawDescGZIP(), []int{6}
}

func (x *ActionMeta) GetName() string {
	if x != nil {
		return x.Name
	}
	return ""
}

var File_mantik_engine_graph_executor_proto protoreflect.FileDescriptor

var file_mantik_engine_graph_executor_proto_rawDesc = []byte{
	0x0a, 0x22, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2f, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2f,
	0x67, 0x72, 0x61, 0x70, 0x68, 0x5f, 0x65, 0x78, 0x65, 0x63, 0x75, 0x74, 0x6f, 0x72, 0x2e, 0x70,
	0x72, 0x6f, 0x74, 0x6f, 0x12, 0x17, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e,
	0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x1a, 0x16, 0x6d,
	0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2f, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2f, 0x64, 0x73, 0x2e,
	0x70, 0x72, 0x6f, 0x74, 0x6f, 0x22, 0xce, 0x01, 0x0a, 0x10, 0x46, 0x65, 0x74, 0x63, 0x68, 0x49,
	0x74, 0x65, 0x6d, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x12, 0x1d, 0x0a, 0x0a, 0x73, 0x65,
	0x73, 0x73, 0x69, 0x6f, 0x6e, 0x5f, 0x69, 0x64, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09, 0x52, 0x09,
	0x73, 0x65, 0x73, 0x73, 0x69, 0x6f, 0x6e, 0x49, 0x64, 0x12, 0x1d, 0x0a, 0x0a, 0x64, 0x61, 0x74,
	0x61, 0x73, 0x65, 0x74, 0x5f, 0x69, 0x64, 0x18, 0x02, 0x20, 0x01, 0x28, 0x09, 0x52, 0x09, 0x64,
	0x61, 0x74, 0x61, 0x73, 0x65, 0x74, 0x49, 0x64, 0x12, 0x43, 0x0a, 0x08, 0x65, 0x6e, 0x63, 0x6f,
	0x64, 0x69, 0x6e, 0x67, 0x18, 0x03, 0x20, 0x01, 0x28, 0x0e, 0x32, 0x27, 0x2e, 0x61, 0x69, 0x2e,
	0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72,
	0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x42, 0x75, 0x6e, 0x64, 0x6c, 0x65, 0x45, 0x6e, 0x63, 0x6f, 0x64,
	0x69, 0x6e, 0x67, 0x52, 0x08, 0x65, 0x6e, 0x63, 0x6f, 0x64, 0x69, 0x6e, 0x67, 0x12, 0x37, 0x0a,
	0x04, 0x6d, 0x65, 0x74, 0x61, 0x18, 0x04, 0x20, 0x01, 0x28, 0x0b, 0x32, 0x23, 0x2e, 0x61, 0x69,
	0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70,
	0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x4d, 0x65, 0x74, 0x61,
	0x52, 0x04, 0x6d, 0x65, 0x74, 0x61, 0x22, 0x4c, 0x0a, 0x11, 0x46, 0x65, 0x74, 0x63, 0x68, 0x49,
	0x74, 0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e, 0x73, 0x65, 0x12, 0x37, 0x0a, 0x06, 0x62,
	0x75, 0x6e, 0x64, 0x6c, 0x65, 0x18, 0x01, 0x20, 0x01, 0x28, 0x0b, 0x32, 0x1f, 0x2e, 0x61, 0x69,
	0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70,
	0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x42, 0x75, 0x6e, 0x64, 0x6c, 0x65, 0x52, 0x06, 0x62, 0x75,
	0x6e, 0x64, 0x6c, 0x65, 0x22, 0x96, 0x01, 0x0a, 0x0f, 0x53, 0x61, 0x76, 0x65, 0x49, 0x74, 0x65,
	0x6d, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x12, 0x1d, 0x0a, 0x0a, 0x73, 0x65, 0x73, 0x73,
	0x69, 0x6f, 0x6e, 0x5f, 0x69, 0x64, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09, 0x52, 0x09, 0x73, 0x65,
	0x73, 0x73, 0x69, 0x6f, 0x6e, 0x49, 0x64, 0x12, 0x17, 0x0a, 0x07, 0x69, 0x74, 0x65, 0x6d, 0x5f,
	0x69, 0x64, 0x18, 0x02, 0x20, 0x01, 0x28, 0x09, 0x52, 0x06, 0x69, 0x74, 0x65, 0x6d, 0x49, 0x64,
	0x12, 0x12, 0x0a, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x18, 0x03, 0x20, 0x01, 0x28, 0x09, 0x52, 0x04,
	0x6e, 0x61, 0x6d, 0x65, 0x12, 0x37, 0x0a, 0x04, 0x6d, 0x65, 0x74, 0x61, 0x18, 0x04, 0x20, 0x01,
	0x28, 0x0b, 0x32, 0x23, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65,
	0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x41, 0x63, 0x74,
	0x69, 0x6f, 0x6e, 0x4d, 0x65, 0x74, 0x61, 0x52, 0x04, 0x6d, 0x65, 0x74, 0x61, 0x22, 0x85, 0x01,
	0x0a, 0x10, 0x53, 0x61, 0x76, 0x65, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e,
	0x73, 0x65, 0x12, 0x12, 0x0a, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09,
	0x52, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x12, 0x24, 0x0a, 0x0e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b,
	0x5f, 0x69, 0x74, 0x65, 0x6d, 0x5f, 0x69, 0x64, 0x18, 0x02, 0x20, 0x01, 0x28, 0x09, 0x52, 0x0c,
	0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x49, 0x74, 0x65, 0x6d, 0x49, 0x64, 0x12, 0x37, 0x0a, 0x04,
	0x6d, 0x65, 0x74, 0x61, 0x18, 0x03, 0x20, 0x01, 0x28, 0x0b, 0x32, 0x23, 0x2e, 0x61, 0x69, 0x2e,
	0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72,
	0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x4d, 0x65, 0x74, 0x61, 0x52,
	0x04, 0x6d, 0x65, 0x74, 0x61, 0x22, 0xc4, 0x01, 0x0a, 0x11, 0x44, 0x65, 0x70, 0x6c, 0x6f, 0x79,
	0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x12, 0x1d, 0x0a, 0x0a, 0x73,
	0x65, 0x73, 0x73, 0x69, 0x6f, 0x6e, 0x5f, 0x69, 0x64, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09, 0x52,
	0x09, 0x73, 0x65, 0x73, 0x73, 0x69, 0x6f, 0x6e, 0x49, 0x64, 0x12, 0x17, 0x0a, 0x07, 0x69, 0x74,
	0x65, 0x6d, 0x5f, 0x69, 0x64, 0x18, 0x02, 0x20, 0x01, 0x28, 0x09, 0x52, 0x06, 0x69, 0x74, 0x65,
	0x6d, 0x49, 0x64, 0x12, 0x21, 0x0a, 0x0c, 0x69, 0x6e, 0x67, 0x72, 0x65, 0x73, 0x73, 0x5f, 0x6e,
	0x61, 0x6d, 0x65, 0x18, 0x03, 0x20, 0x01, 0x28, 0x09, 0x52, 0x0b, 0x69, 0x6e, 0x67, 0x72, 0x65,
	0x73, 0x73, 0x4e, 0x61, 0x6d, 0x65, 0x12, 0x1b, 0x0a, 0x09, 0x6e, 0x61, 0x6d, 0x65, 0x5f, 0x68,
	0x69, 0x6e, 0x74, 0x18, 0x04, 0x20, 0x01, 0x28, 0x09, 0x52, 0x08, 0x6e, 0x61, 0x6d, 0x65, 0x48,
	0x69, 0x6e, 0x74, 0x12, 0x37, 0x0a, 0x04, 0x6d, 0x65, 0x74, 0x61, 0x18, 0x05, 0x20, 0x01, 0x28,
	0x0b, 0x32, 0x23, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e,
	0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x41, 0x63, 0x74, 0x69,
	0x6f, 0x6e, 0x4d, 0x65, 0x74, 0x61, 0x52, 0x04, 0x6d, 0x65, 0x74, 0x61, 0x22, 0x6e, 0x0a, 0x12,
	0x44, 0x65, 0x70, 0x6c, 0x6f, 0x79, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e,
	0x73, 0x65, 0x12, 0x12, 0x0a, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09,
	0x52, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x12, 0x21, 0x0a, 0x0c, 0x69, 0x6e, 0x74, 0x65, 0x72, 0x6e,
	0x61, 0x6c, 0x5f, 0x75, 0x72, 0x6c, 0x18, 0x02, 0x20, 0x01, 0x28, 0x09, 0x52, 0x0b, 0x69, 0x6e,
	0x74, 0x65, 0x72, 0x6e, 0x61, 0x6c, 0x55, 0x72, 0x6c, 0x12, 0x21, 0x0a, 0x0c, 0x65, 0x78, 0x74,
	0x65, 0x72, 0x6e, 0x61, 0x6c, 0x5f, 0x75, 0x72, 0x6c, 0x18, 0x03, 0x20, 0x01, 0x28, 0x09, 0x52,
	0x0b, 0x65, 0x78, 0x74, 0x65, 0x72, 0x6e, 0x61, 0x6c, 0x55, 0x72, 0x6c, 0x22, 0x20, 0x0a, 0x0a,
	0x41, 0x63, 0x74, 0x69, 0x6f, 0x6e, 0x4d, 0x65, 0x74, 0x61, 0x12, 0x12, 0x0a, 0x04, 0x6e, 0x61,
	0x6d, 0x65, 0x18, 0x01, 0x20, 0x01, 0x28, 0x09, 0x52, 0x04, 0x6e, 0x61, 0x6d, 0x65, 0x32, 0xcb,
	0x02, 0x0a, 0x14, 0x47, 0x72, 0x61, 0x70, 0x68, 0x45, 0x78, 0x65, 0x63, 0x75, 0x74, 0x6f, 0x72,
	0x53, 0x65, 0x72, 0x76, 0x69, 0x63, 0x65, 0x12, 0x67, 0x0a, 0x0c, 0x46, 0x65, 0x74, 0x63, 0x68,
	0x44, 0x61, 0x74, 0x61, 0x53, 0x65, 0x74, 0x12, 0x29, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e,
	0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f,
	0x73, 0x2e, 0x46, 0x65, 0x74, 0x63, 0x68, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x71, 0x75, 0x65,
	0x73, 0x74, 0x1a, 0x2a, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65,
	0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x46, 0x65, 0x74,
	0x63, 0x68, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e, 0x73, 0x65, 0x22, 0x00,
	0x12, 0x61, 0x0a, 0x08, 0x53, 0x61, 0x76, 0x65, 0x49, 0x74, 0x65, 0x6d, 0x12, 0x28, 0x2e, 0x61,
	0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e,
	0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x53, 0x61, 0x76, 0x65, 0x49, 0x74, 0x65, 0x6d, 0x52,
	0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x1a, 0x29, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74,
	0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73,
	0x2e, 0x53, 0x61, 0x76, 0x65, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e, 0x73,
	0x65, 0x22, 0x00, 0x12, 0x67, 0x0a, 0x0a, 0x44, 0x65, 0x70, 0x6c, 0x6f, 0x79, 0x49, 0x74, 0x65,
	0x6d, 0x12, 0x2a, 0x2e, 0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e,
	0x67, 0x69, 0x6e, 0x65, 0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x44, 0x65, 0x70, 0x6c,
	0x6f, 0x79, 0x49, 0x74, 0x65, 0x6d, 0x52, 0x65, 0x71, 0x75, 0x65, 0x73, 0x74, 0x1a, 0x2b, 0x2e,
	0x61, 0x69, 0x2e, 0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2e, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65,
	0x2e, 0x70, 0x72, 0x6f, 0x74, 0x6f, 0x73, 0x2e, 0x44, 0x65, 0x70, 0x6c, 0x6f, 0x79, 0x49, 0x74,
	0x65, 0x6d, 0x52, 0x65, 0x73, 0x70, 0x6f, 0x6e, 0x73, 0x65, 0x22, 0x00, 0x42, 0x0f, 0x5a, 0x0d,
	0x6d, 0x61, 0x6e, 0x74, 0x69, 0x6b, 0x2f, 0x65, 0x6e, 0x67, 0x69, 0x6e, 0x65, 0x62, 0x06, 0x70,
	0x72, 0x6f, 0x74, 0x6f, 0x33,
}

var (
	file_mantik_engine_graph_executor_proto_rawDescOnce sync.Once
	file_mantik_engine_graph_executor_proto_rawDescData = file_mantik_engine_graph_executor_proto_rawDesc
)

func file_mantik_engine_graph_executor_proto_rawDescGZIP() []byte {
	file_mantik_engine_graph_executor_proto_rawDescOnce.Do(func() {
		file_mantik_engine_graph_executor_proto_rawDescData = protoimpl.X.CompressGZIP(file_mantik_engine_graph_executor_proto_rawDescData)
	})
	return file_mantik_engine_graph_executor_proto_rawDescData
}

var file_mantik_engine_graph_executor_proto_msgTypes = make([]protoimpl.MessageInfo, 7)
var file_mantik_engine_graph_executor_proto_goTypes = []interface{}{
	(*FetchItemRequest)(nil),   // 0: ai.mantik.engine.protos.FetchItemRequest
	(*FetchItemResponse)(nil),  // 1: ai.mantik.engine.protos.FetchItemResponse
	(*SaveItemRequest)(nil),    // 2: ai.mantik.engine.protos.SaveItemRequest
	(*SaveItemResponse)(nil),   // 3: ai.mantik.engine.protos.SaveItemResponse
	(*DeployItemRequest)(nil),  // 4: ai.mantik.engine.protos.DeployItemRequest
	(*DeployItemResponse)(nil), // 5: ai.mantik.engine.protos.DeployItemResponse
	(*ActionMeta)(nil),         // 6: ai.mantik.engine.protos.ActionMeta
	(BundleEncoding)(0),        // 7: ai.mantik.engine.protos.BundleEncoding
	(*Bundle)(nil),             // 8: ai.mantik.engine.protos.Bundle
}
var file_mantik_engine_graph_executor_proto_depIdxs = []int32{
	7, // 0: ai.mantik.engine.protos.FetchItemRequest.encoding:type_name -> ai.mantik.engine.protos.BundleEncoding
	6, // 1: ai.mantik.engine.protos.FetchItemRequest.meta:type_name -> ai.mantik.engine.protos.ActionMeta
	8, // 2: ai.mantik.engine.protos.FetchItemResponse.bundle:type_name -> ai.mantik.engine.protos.Bundle
	6, // 3: ai.mantik.engine.protos.SaveItemRequest.meta:type_name -> ai.mantik.engine.protos.ActionMeta
	6, // 4: ai.mantik.engine.protos.SaveItemResponse.meta:type_name -> ai.mantik.engine.protos.ActionMeta
	6, // 5: ai.mantik.engine.protos.DeployItemRequest.meta:type_name -> ai.mantik.engine.protos.ActionMeta
	0, // 6: ai.mantik.engine.protos.GraphExecutorService.FetchDataSet:input_type -> ai.mantik.engine.protos.FetchItemRequest
	2, // 7: ai.mantik.engine.protos.GraphExecutorService.SaveItem:input_type -> ai.mantik.engine.protos.SaveItemRequest
	4, // 8: ai.mantik.engine.protos.GraphExecutorService.DeployItem:input_type -> ai.mantik.engine.protos.DeployItemRequest
	1, // 9: ai.mantik.engine.protos.GraphExecutorService.FetchDataSet:output_type -> ai.mantik.engine.protos.FetchItemResponse
	3, // 10: ai.mantik.engine.protos.GraphExecutorService.SaveItem:output_type -> ai.mantik.engine.protos.SaveItemResponse
	5, // 11: ai.mantik.engine.protos.GraphExecutorService.DeployItem:output_type -> ai.mantik.engine.protos.DeployItemResponse
	9, // [9:12] is the sub-list for method output_type
	6, // [6:9] is the sub-list for method input_type
	6, // [6:6] is the sub-list for extension type_name
	6, // [6:6] is the sub-list for extension extendee
	0, // [0:6] is the sub-list for field type_name
}

func init() { file_mantik_engine_graph_executor_proto_init() }
func file_mantik_engine_graph_executor_proto_init() {
	if File_mantik_engine_graph_executor_proto != nil {
		return
	}
	file_mantik_engine_ds_proto_init()
	if !protoimpl.UnsafeEnabled {
		file_mantik_engine_graph_executor_proto_msgTypes[0].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*FetchItemRequest); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[1].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*FetchItemResponse); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[2].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*SaveItemRequest); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[3].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*SaveItemResponse); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[4].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*DeployItemRequest); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[5].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*DeployItemResponse); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
		file_mantik_engine_graph_executor_proto_msgTypes[6].Exporter = func(v interface{}, i int) interface{} {
			switch v := v.(*ActionMeta); i {
			case 0:
				return &v.state
			case 1:
				return &v.sizeCache
			case 2:
				return &v.unknownFields
			default:
				return nil
			}
		}
	}
	type x struct{}
	out := protoimpl.TypeBuilder{
		File: protoimpl.DescBuilder{
			GoPackagePath: reflect.TypeOf(x{}).PkgPath(),
			RawDescriptor: file_mantik_engine_graph_executor_proto_rawDesc,
			NumEnums:      0,
			NumMessages:   7,
			NumExtensions: 0,
			NumServices:   1,
		},
		GoTypes:           file_mantik_engine_graph_executor_proto_goTypes,
		DependencyIndexes: file_mantik_engine_graph_executor_proto_depIdxs,
		MessageInfos:      file_mantik_engine_graph_executor_proto_msgTypes,
	}.Build()
	File_mantik_engine_graph_executor_proto = out.File
	file_mantik_engine_graph_executor_proto_rawDesc = nil
	file_mantik_engine_graph_executor_proto_goTypes = nil
	file_mantik_engine_graph_executor_proto_depIdxs = nil
}

// Reference imports to suppress errors if they are not otherwise used.
var _ context.Context
var _ grpc.ClientConnInterface

// This is a compile-time assertion to ensure that this generated file
// is compatible with the grpc package it is being compiled against.
const _ = grpc.SupportPackageIsVersion6

// GraphExecutorServiceClient is the client API for GraphExecutorService service.
//
// For semantics around ctx use and closing/ending streaming RPCs, please refer to https://godoc.org/google.golang.org/grpc#ClientConn.NewStream.
type GraphExecutorServiceClient interface {
	//* Evaluates and fetches the content of a dataset.
	FetchDataSet(ctx context.Context, in *FetchItemRequest, opts ...grpc.CallOption) (*FetchItemResponse, error)
	//* Evaluates and saves an item to the repository.
	SaveItem(ctx context.Context, in *SaveItemRequest, opts ...grpc.CallOption) (*SaveItemResponse, error)
	//* Deploys an Item to the cluster.
	DeployItem(ctx context.Context, in *DeployItemRequest, opts ...grpc.CallOption) (*DeployItemResponse, error)
}

type graphExecutorServiceClient struct {
	cc grpc.ClientConnInterface
}

func NewGraphExecutorServiceClient(cc grpc.ClientConnInterface) GraphExecutorServiceClient {
	return &graphExecutorServiceClient{cc}
}

func (c *graphExecutorServiceClient) FetchDataSet(ctx context.Context, in *FetchItemRequest, opts ...grpc.CallOption) (*FetchItemResponse, error) {
	out := new(FetchItemResponse)
	err := c.cc.Invoke(ctx, "/ai.mantik.engine.protos.GraphExecutorService/FetchDataSet", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *graphExecutorServiceClient) SaveItem(ctx context.Context, in *SaveItemRequest, opts ...grpc.CallOption) (*SaveItemResponse, error) {
	out := new(SaveItemResponse)
	err := c.cc.Invoke(ctx, "/ai.mantik.engine.protos.GraphExecutorService/SaveItem", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

func (c *graphExecutorServiceClient) DeployItem(ctx context.Context, in *DeployItemRequest, opts ...grpc.CallOption) (*DeployItemResponse, error) {
	out := new(DeployItemResponse)
	err := c.cc.Invoke(ctx, "/ai.mantik.engine.protos.GraphExecutorService/DeployItem", in, out, opts...)
	if err != nil {
		return nil, err
	}
	return out, nil
}

// GraphExecutorServiceServer is the server API for GraphExecutorService service.
type GraphExecutorServiceServer interface {
	//* Evaluates and fetches the content of a dataset.
	FetchDataSet(context.Context, *FetchItemRequest) (*FetchItemResponse, error)
	//* Evaluates and saves an item to the repository.
	SaveItem(context.Context, *SaveItemRequest) (*SaveItemResponse, error)
	//* Deploys an Item to the cluster.
	DeployItem(context.Context, *DeployItemRequest) (*DeployItemResponse, error)
}

// UnimplementedGraphExecutorServiceServer can be embedded to have forward compatible implementations.
type UnimplementedGraphExecutorServiceServer struct {
}

func (*UnimplementedGraphExecutorServiceServer) FetchDataSet(context.Context, *FetchItemRequest) (*FetchItemResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method FetchDataSet not implemented")
}
func (*UnimplementedGraphExecutorServiceServer) SaveItem(context.Context, *SaveItemRequest) (*SaveItemResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method SaveItem not implemented")
}
func (*UnimplementedGraphExecutorServiceServer) DeployItem(context.Context, *DeployItemRequest) (*DeployItemResponse, error) {
	return nil, status.Errorf(codes.Unimplemented, "method DeployItem not implemented")
}

func RegisterGraphExecutorServiceServer(s *grpc.Server, srv GraphExecutorServiceServer) {
	s.RegisterService(&_GraphExecutorService_serviceDesc, srv)
}

func _GraphExecutorService_FetchDataSet_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(FetchItemRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(GraphExecutorServiceServer).FetchDataSet(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/ai.mantik.engine.protos.GraphExecutorService/FetchDataSet",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(GraphExecutorServiceServer).FetchDataSet(ctx, req.(*FetchItemRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _GraphExecutorService_SaveItem_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(SaveItemRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(GraphExecutorServiceServer).SaveItem(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/ai.mantik.engine.protos.GraphExecutorService/SaveItem",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(GraphExecutorServiceServer).SaveItem(ctx, req.(*SaveItemRequest))
	}
	return interceptor(ctx, in, info, handler)
}

func _GraphExecutorService_DeployItem_Handler(srv interface{}, ctx context.Context, dec func(interface{}) error, interceptor grpc.UnaryServerInterceptor) (interface{}, error) {
	in := new(DeployItemRequest)
	if err := dec(in); err != nil {
		return nil, err
	}
	if interceptor == nil {
		return srv.(GraphExecutorServiceServer).DeployItem(ctx, in)
	}
	info := &grpc.UnaryServerInfo{
		Server:     srv,
		FullMethod: "/ai.mantik.engine.protos.GraphExecutorService/DeployItem",
	}
	handler := func(ctx context.Context, req interface{}) (interface{}, error) {
		return srv.(GraphExecutorServiceServer).DeployItem(ctx, req.(*DeployItemRequest))
	}
	return interceptor(ctx, in, info, handler)
}

var _GraphExecutorService_serviceDesc = grpc.ServiceDesc{
	ServiceName: "ai.mantik.engine.protos.GraphExecutorService",
	HandlerType: (*GraphExecutorServiceServer)(nil),
	Methods: []grpc.MethodDesc{
		{
			MethodName: "FetchDataSet",
			Handler:    _GraphExecutorService_FetchDataSet_Handler,
		},
		{
			MethodName: "SaveItem",
			Handler:    _GraphExecutorService_SaveItem_Handler,
		},
		{
			MethodName: "DeployItem",
			Handler:    _GraphExecutorService_DeployItem_Handler,
		},
	},
	Streams:  []grpc.StreamDesc{},
	Metadata: "mantik/engine/graph_executor.proto",
}
