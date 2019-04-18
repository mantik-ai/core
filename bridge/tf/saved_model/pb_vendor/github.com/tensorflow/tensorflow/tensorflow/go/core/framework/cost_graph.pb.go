// Code generated by protoc-gen-go. DO NOT EDIT.
// source: tensorflow/core/framework/cost_graph.proto

package framework // import "tfbridge/pb_vendor/github.com/tensorflow/tensorflow/tensorflow/go/core/framework"

import proto "github.com/golang/protobuf/proto"
import fmt "fmt"
import math "math"

// Reference imports to suppress errors if they are not otherwise used.
var _ = proto.Marshal
var _ = fmt.Errorf
var _ = math.Inf

// This is a compile-time assertion to ensure that this generated file
// is compatible with the proto package it is being compiled against.
// A compilation error at this line likely means your copy of the
// proto package needs to be updated.
const _ = proto.ProtoPackageIsVersion2 // please upgrade the proto package

type CostGraphDef struct {
	Node                 []*CostGraphDef_Node `protobuf:"bytes,1,rep,name=node,proto3" json:"node,omitempty"`
	XXX_NoUnkeyedLiteral struct{}             `json:"-"`
	XXX_unrecognized     []byte               `json:"-"`
	XXX_sizecache        int32                `json:"-"`
}

func (m *CostGraphDef) Reset()         { *m = CostGraphDef{} }
func (m *CostGraphDef) String() string { return proto.CompactTextString(m) }
func (*CostGraphDef) ProtoMessage()    {}
func (*CostGraphDef) Descriptor() ([]byte, []int) {
	return fileDescriptor_cost_graph_c94952d84d809304, []int{0}
}
func (m *CostGraphDef) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_CostGraphDef.Unmarshal(m, b)
}
func (m *CostGraphDef) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_CostGraphDef.Marshal(b, m, deterministic)
}
func (dst *CostGraphDef) XXX_Merge(src proto.Message) {
	xxx_messageInfo_CostGraphDef.Merge(dst, src)
}
func (m *CostGraphDef) XXX_Size() int {
	return xxx_messageInfo_CostGraphDef.Size(m)
}
func (m *CostGraphDef) XXX_DiscardUnknown() {
	xxx_messageInfo_CostGraphDef.DiscardUnknown(m)
}

var xxx_messageInfo_CostGraphDef proto.InternalMessageInfo

func (m *CostGraphDef) GetNode() []*CostGraphDef_Node {
	if m != nil {
		return m.Node
	}
	return nil
}

type CostGraphDef_Node struct {
	// The name of the node. Names are globally unique.
	Name string `protobuf:"bytes,1,opt,name=name,proto3" json:"name,omitempty"`
	// The device of the node. Can be empty if the node is mapped to the
	// default partition or partitioning hasn't been run yet.
	Device string `protobuf:"bytes,2,opt,name=device,proto3" json:"device,omitempty"`
	// The id of the node. Node ids are only unique inside a partition.
	Id         int32                           `protobuf:"varint,3,opt,name=id,proto3" json:"id,omitempty"`
	InputInfo  []*CostGraphDef_Node_InputInfo  `protobuf:"bytes,4,rep,name=input_info,json=inputInfo,proto3" json:"input_info,omitempty"`
	OutputInfo []*CostGraphDef_Node_OutputInfo `protobuf:"bytes,5,rep,name=output_info,json=outputInfo,proto3" json:"output_info,omitempty"`
	// Temporary memory used by this node.
	TemporaryMemorySize int64 `protobuf:"varint,6,opt,name=temporary_memory_size,json=temporaryMemorySize,proto3" json:"temporary_memory_size,omitempty"`
	// Persistent memory used by this node.
	PersistentMemorySize       int64 `protobuf:"varint,12,opt,name=persistent_memory_size,json=persistentMemorySize,proto3" json:"persistent_memory_size,omitempty"`
	HostTempMemorySize         int64 `protobuf:"varint,10,opt,name=host_temp_memory_size,json=hostTempMemorySize,proto3" json:"host_temp_memory_size,omitempty"`                         // Deprecated: Do not use.
	DeviceTempMemorySize       int64 `protobuf:"varint,11,opt,name=device_temp_memory_size,json=deviceTempMemorySize,proto3" json:"device_temp_memory_size,omitempty"`                   // Deprecated: Do not use.
	DevicePersistentMemorySize int64 `protobuf:"varint,16,opt,name=device_persistent_memory_size,json=devicePersistentMemorySize,proto3" json:"device_persistent_memory_size,omitempty"` // Deprecated: Do not use.
	// Estimate of the computational cost of this node, in microseconds.
	ComputeCost int64 `protobuf:"varint,9,opt,name=compute_cost,json=computeCost,proto3" json:"compute_cost,omitempty"`
	// Analytical estimate of the computational cost of this node, in
	// microseconds.
	ComputeTime int64 `protobuf:"varint,14,opt,name=compute_time,json=computeTime,proto3" json:"compute_time,omitempty"`
	// Analytical estimate of the memory access cost of this node, in
	// microseconds.
	MemoryTime int64 `protobuf:"varint,15,opt,name=memory_time,json=memoryTime,proto3" json:"memory_time,omitempty"`
	// If true, the output is permanent: it can't be discarded, because this
	// node is part of the "final output". Nodes may depend on final nodes.
	IsFinal bool `protobuf:"varint,7,opt,name=is_final,json=isFinal,proto3" json:"is_final,omitempty"`
	// Ids of the control inputs for this node.
	ControlInput []int32 `protobuf:"varint,8,rep,packed,name=control_input,json=controlInput,proto3" json:"control_input,omitempty"`
	// Are the costs inaccurate?
	Inaccurate           bool     `protobuf:"varint,17,opt,name=inaccurate,proto3" json:"inaccurate,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *CostGraphDef_Node) Reset()         { *m = CostGraphDef_Node{} }
func (m *CostGraphDef_Node) String() string { return proto.CompactTextString(m) }
func (*CostGraphDef_Node) ProtoMessage()    {}
func (*CostGraphDef_Node) Descriptor() ([]byte, []int) {
	return fileDescriptor_cost_graph_c94952d84d809304, []int{0, 0}
}
func (m *CostGraphDef_Node) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_CostGraphDef_Node.Unmarshal(m, b)
}
func (m *CostGraphDef_Node) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_CostGraphDef_Node.Marshal(b, m, deterministic)
}
func (dst *CostGraphDef_Node) XXX_Merge(src proto.Message) {
	xxx_messageInfo_CostGraphDef_Node.Merge(dst, src)
}
func (m *CostGraphDef_Node) XXX_Size() int {
	return xxx_messageInfo_CostGraphDef_Node.Size(m)
}
func (m *CostGraphDef_Node) XXX_DiscardUnknown() {
	xxx_messageInfo_CostGraphDef_Node.DiscardUnknown(m)
}

var xxx_messageInfo_CostGraphDef_Node proto.InternalMessageInfo

func (m *CostGraphDef_Node) GetName() string {
	if m != nil {
		return m.Name
	}
	return ""
}

func (m *CostGraphDef_Node) GetDevice() string {
	if m != nil {
		return m.Device
	}
	return ""
}

func (m *CostGraphDef_Node) GetId() int32 {
	if m != nil {
		return m.Id
	}
	return 0
}

func (m *CostGraphDef_Node) GetInputInfo() []*CostGraphDef_Node_InputInfo {
	if m != nil {
		return m.InputInfo
	}
	return nil
}

func (m *CostGraphDef_Node) GetOutputInfo() []*CostGraphDef_Node_OutputInfo {
	if m != nil {
		return m.OutputInfo
	}
	return nil
}

func (m *CostGraphDef_Node) GetTemporaryMemorySize() int64 {
	if m != nil {
		return m.TemporaryMemorySize
	}
	return 0
}

func (m *CostGraphDef_Node) GetPersistentMemorySize() int64 {
	if m != nil {
		return m.PersistentMemorySize
	}
	return 0
}

// Deprecated: Do not use.
func (m *CostGraphDef_Node) GetHostTempMemorySize() int64 {
	if m != nil {
		return m.HostTempMemorySize
	}
	return 0
}

// Deprecated: Do not use.
func (m *CostGraphDef_Node) GetDeviceTempMemorySize() int64 {
	if m != nil {
		return m.DeviceTempMemorySize
	}
	return 0
}

// Deprecated: Do not use.
func (m *CostGraphDef_Node) GetDevicePersistentMemorySize() int64 {
	if m != nil {
		return m.DevicePersistentMemorySize
	}
	return 0
}

func (m *CostGraphDef_Node) GetComputeCost() int64 {
	if m != nil {
		return m.ComputeCost
	}
	return 0
}

func (m *CostGraphDef_Node) GetComputeTime() int64 {
	if m != nil {
		return m.ComputeTime
	}
	return 0
}

func (m *CostGraphDef_Node) GetMemoryTime() int64 {
	if m != nil {
		return m.MemoryTime
	}
	return 0
}

func (m *CostGraphDef_Node) GetIsFinal() bool {
	if m != nil {
		return m.IsFinal
	}
	return false
}

func (m *CostGraphDef_Node) GetControlInput() []int32 {
	if m != nil {
		return m.ControlInput
	}
	return nil
}

func (m *CostGraphDef_Node) GetInaccurate() bool {
	if m != nil {
		return m.Inaccurate
	}
	return false
}

// Inputs of this node. They must be executed before this node can be
// executed. An input is a particular output of another node, specified
// by the node id and the output index.
type CostGraphDef_Node_InputInfo struct {
	PrecedingNode        int32    `protobuf:"varint,1,opt,name=preceding_node,json=precedingNode,proto3" json:"preceding_node,omitempty"`
	PrecedingPort        int32    `protobuf:"varint,2,opt,name=preceding_port,json=precedingPort,proto3" json:"preceding_port,omitempty"`
	XXX_NoUnkeyedLiteral struct{} `json:"-"`
	XXX_unrecognized     []byte   `json:"-"`
	XXX_sizecache        int32    `json:"-"`
}

func (m *CostGraphDef_Node_InputInfo) Reset()         { *m = CostGraphDef_Node_InputInfo{} }
func (m *CostGraphDef_Node_InputInfo) String() string { return proto.CompactTextString(m) }
func (*CostGraphDef_Node_InputInfo) ProtoMessage()    {}
func (*CostGraphDef_Node_InputInfo) Descriptor() ([]byte, []int) {
	return fileDescriptor_cost_graph_c94952d84d809304, []int{0, 0, 0}
}
func (m *CostGraphDef_Node_InputInfo) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_CostGraphDef_Node_InputInfo.Unmarshal(m, b)
}
func (m *CostGraphDef_Node_InputInfo) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_CostGraphDef_Node_InputInfo.Marshal(b, m, deterministic)
}
func (dst *CostGraphDef_Node_InputInfo) XXX_Merge(src proto.Message) {
	xxx_messageInfo_CostGraphDef_Node_InputInfo.Merge(dst, src)
}
func (m *CostGraphDef_Node_InputInfo) XXX_Size() int {
	return xxx_messageInfo_CostGraphDef_Node_InputInfo.Size(m)
}
func (m *CostGraphDef_Node_InputInfo) XXX_DiscardUnknown() {
	xxx_messageInfo_CostGraphDef_Node_InputInfo.DiscardUnknown(m)
}

var xxx_messageInfo_CostGraphDef_Node_InputInfo proto.InternalMessageInfo

func (m *CostGraphDef_Node_InputInfo) GetPrecedingNode() int32 {
	if m != nil {
		return m.PrecedingNode
	}
	return 0
}

func (m *CostGraphDef_Node_InputInfo) GetPrecedingPort() int32 {
	if m != nil {
		return m.PrecedingPort
	}
	return 0
}

// Outputs of this node.
type CostGraphDef_Node_OutputInfo struct {
	Size int64 `protobuf:"varint,1,opt,name=size,proto3" json:"size,omitempty"`
	// If >= 0, the output is an alias of an input. Note that an alias input
	// may itself be an alias. The algorithm will therefore need to follow
	// those pointers.
	AliasInputPort       int64             `protobuf:"varint,2,opt,name=alias_input_port,json=aliasInputPort,proto3" json:"alias_input_port,omitempty"`
	Shape                *TensorShapeProto `protobuf:"bytes,3,opt,name=shape,proto3" json:"shape,omitempty"`
	Dtype                DataType          `protobuf:"varint,4,opt,name=dtype,proto3,enum=tensorflow.DataType" json:"dtype,omitempty"`
	XXX_NoUnkeyedLiteral struct{}          `json:"-"`
	XXX_unrecognized     []byte            `json:"-"`
	XXX_sizecache        int32             `json:"-"`
}

func (m *CostGraphDef_Node_OutputInfo) Reset()         { *m = CostGraphDef_Node_OutputInfo{} }
func (m *CostGraphDef_Node_OutputInfo) String() string { return proto.CompactTextString(m) }
func (*CostGraphDef_Node_OutputInfo) ProtoMessage()    {}
func (*CostGraphDef_Node_OutputInfo) Descriptor() ([]byte, []int) {
	return fileDescriptor_cost_graph_c94952d84d809304, []int{0, 0, 1}
}
func (m *CostGraphDef_Node_OutputInfo) XXX_Unmarshal(b []byte) error {
	return xxx_messageInfo_CostGraphDef_Node_OutputInfo.Unmarshal(m, b)
}
func (m *CostGraphDef_Node_OutputInfo) XXX_Marshal(b []byte, deterministic bool) ([]byte, error) {
	return xxx_messageInfo_CostGraphDef_Node_OutputInfo.Marshal(b, m, deterministic)
}
func (dst *CostGraphDef_Node_OutputInfo) XXX_Merge(src proto.Message) {
	xxx_messageInfo_CostGraphDef_Node_OutputInfo.Merge(dst, src)
}
func (m *CostGraphDef_Node_OutputInfo) XXX_Size() int {
	return xxx_messageInfo_CostGraphDef_Node_OutputInfo.Size(m)
}
func (m *CostGraphDef_Node_OutputInfo) XXX_DiscardUnknown() {
	xxx_messageInfo_CostGraphDef_Node_OutputInfo.DiscardUnknown(m)
}

var xxx_messageInfo_CostGraphDef_Node_OutputInfo proto.InternalMessageInfo

func (m *CostGraphDef_Node_OutputInfo) GetSize() int64 {
	if m != nil {
		return m.Size
	}
	return 0
}

func (m *CostGraphDef_Node_OutputInfo) GetAliasInputPort() int64 {
	if m != nil {
		return m.AliasInputPort
	}
	return 0
}

func (m *CostGraphDef_Node_OutputInfo) GetShape() *TensorShapeProto {
	if m != nil {
		return m.Shape
	}
	return nil
}

func (m *CostGraphDef_Node_OutputInfo) GetDtype() DataType {
	if m != nil {
		return m.Dtype
	}
	return DataType_DT_INVALID
}

func init() {
	proto.RegisterType((*CostGraphDef)(nil), "tensorflow.CostGraphDef")
	proto.RegisterType((*CostGraphDef_Node)(nil), "tensorflow.CostGraphDef.Node")
	proto.RegisterType((*CostGraphDef_Node_InputInfo)(nil), "tensorflow.CostGraphDef.Node.InputInfo")
	proto.RegisterType((*CostGraphDef_Node_OutputInfo)(nil), "tensorflow.CostGraphDef.Node.OutputInfo")
}

func init() {
	proto.RegisterFile("tensorflow/core/framework/cost_graph.proto", fileDescriptor_cost_graph_c94952d84d809304)
}

var fileDescriptor_cost_graph_c94952d84d809304 = []byte{
	// 628 bytes of a gzipped FileDescriptorProto
	0x1f, 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0xff, 0x7c, 0x94, 0xcf, 0x6e, 0xd3, 0x4e,
	0x10, 0xc7, 0xe5, 0xfc, 0x69, 0x93, 0x49, 0x9a, 0xf6, 0xb7, 0xbf, 0xb6, 0x2c, 0x11, 0x85, 0x00,
	0xaa, 0xb0, 0x2a, 0x94, 0x88, 0x00, 0x07, 0x0e, 0x5c, 0x4a, 0x29, 0xea, 0x01, 0x88, 0xdc, 0x5c,
	0xe0, 0x62, 0xb9, 0xf6, 0x26, 0x59, 0x11, 0x7b, 0xac, 0xdd, 0x0d, 0x55, 0xfa, 0x0a, 0xbc, 0x09,
	0x2f, 0xc2, 0x2b, 0x71, 0x44, 0x3b, 0x36, 0x8e, 0x53, 0xda, 0xde, 0xc6, 0x33, 0x9f, 0xef, 0x77,
	0xff, 0x78, 0x66, 0xe1, 0xc8, 0x88, 0x44, 0xa3, 0x9a, 0xcc, 0xf1, 0x72, 0x10, 0xa2, 0x12, 0x83,
	0x89, 0x0a, 0x62, 0x71, 0x89, 0xea, 0xdb, 0x20, 0x44, 0x6d, 0xfc, 0xa9, 0x0a, 0xd2, 0x59, 0x3f,
	0x55, 0x68, 0x90, 0xc1, 0x8a, 0xed, 0x3e, 0xbf, 0x5d, 0x97, 0x55, 0x7c, 0x3d, 0x0b, 0x52, 0x91,
	0x29, 0xbb, 0x87, 0x77, 0xd0, 0xcb, 0x54, 0xe8, 0x0c, 0x7b, 0xf2, 0xa3, 0x01, 0xed, 0x77, 0xa8,
	0xcd, 0x07, 0xbb, 0xe8, 0x89, 0x98, 0xb0, 0x17, 0x50, 0x4b, 0x30, 0x12, 0xdc, 0xe9, 0x55, 0xdd,
	0xd6, 0xf0, 0xa0, 0xbf, 0xb2, 0xe9, 0x97, 0xb9, 0xfe, 0x27, 0x8c, 0x84, 0x47, 0x68, 0xf7, 0xd7,
	0x26, 0xd4, 0xec, 0x27, 0x63, 0x50, 0x4b, 0x82, 0xd8, 0x6a, 0x1d, 0xb7, 0xe9, 0x51, 0xcc, 0xf6,
	0x61, 0x23, 0x12, 0xdf, 0x65, 0x28, 0x78, 0x85, 0xb2, 0xf9, 0x17, 0xeb, 0x40, 0x45, 0x46, 0xbc,
	0xda, 0x73, 0xdc, 0xba, 0x57, 0x91, 0x11, 0x3b, 0x05, 0x90, 0x49, 0xba, 0x30, 0xbe, 0x4c, 0x26,
	0xc8, 0x6b, 0xb4, 0xfa, 0xb3, 0x3b, 0x57, 0xef, 0x9f, 0x59, 0xfe, 0x2c, 0x99, 0xa0, 0xd7, 0x94,
	0x7f, 0x43, 0x76, 0x06, 0x2d, 0x5c, 0x98, 0xc2, 0xa8, 0x4e, 0x46, 0xee, 0xdd, 0x46, 0x9f, 0x49,
	0x40, 0x4e, 0x80, 0x45, 0xcc, 0x86, 0xb0, 0x67, 0x44, 0x9c, 0xa2, 0x0a, 0xd4, 0xd2, 0x8f, 0x45,
	0x8c, 0x6a, 0xe9, 0x6b, 0x79, 0x25, 0xf8, 0x46, 0xcf, 0x71, 0xab, 0xde, 0xff, 0x45, 0xf1, 0x23,
	0xd5, 0xce, 0xe5, 0x95, 0x60, 0xaf, 0x60, 0x3f, 0x15, 0x4a, 0x4b, 0x6d, 0x44, 0x62, 0xd6, 0x44,
	0x6d, 0x12, 0xed, 0xae, 0xaa, 0x25, 0xd5, 0x6b, 0xd8, 0x9b, 0xd9, 0x5f, 0x6f, 0x1d, 0xd7, 0x44,
	0x60, 0x45, 0xc7, 0x15, 0xee, 0x78, 0xcc, 0x02, 0x63, 0x11, 0xa7, 0x25, 0xd9, 0x1b, 0xb8, 0x97,
	0xdd, 0xe6, 0xbf, 0xc2, 0x56, 0x21, 0xdc, 0xcd, 0x90, 0x6b, 0xd2, 0xf7, 0x70, 0x90, 0x4b, 0x6f,
	0xd9, 0xee, 0x4e, 0x61, 0xd0, 0xcd, 0xc0, 0xd1, 0x4d, 0x1b, 0x7f, 0x0c, 0xed, 0x10, 0xe3, 0x74,
	0x61, 0x84, 0x6f, 0x7b, 0x97, 0x37, 0xe9, 0x90, 0xad, 0x3c, 0x67, 0x6f, 0xba, 0x8c, 0x18, 0x19,
	0x0b, 0xde, 0x59, 0x43, 0xc6, 0x32, 0x16, 0xec, 0x11, 0xb4, 0xf2, 0xa5, 0x89, 0xd8, 0x26, 0x02,
	0xb2, 0x14, 0x01, 0xf7, 0xa1, 0x21, 0xb5, 0x3f, 0x91, 0x49, 0x30, 0xe7, 0x9b, 0x3d, 0xc7, 0x6d,
	0x78, 0x9b, 0x52, 0x9f, 0xda, 0x4f, 0xf6, 0x14, 0xb6, 0x42, 0x4c, 0x8c, 0xc2, 0xb9, 0x4f, 0x4d,
	0xc0, 0x1b, 0xbd, 0xaa, 0x5b, 0xf7, 0xda, 0x79, 0x92, 0x7a, 0x84, 0x3d, 0xb4, 0xcd, 0x15, 0x84,
	0xe1, 0x42, 0x05, 0x46, 0xf0, 0xff, 0xc8, 0xa1, 0x94, 0xe9, 0x7e, 0x81, 0x66, 0xd1, 0x4c, 0xec,
	0x10, 0x3a, 0xa9, 0x12, 0xa1, 0x88, 0x64, 0x32, 0xf5, 0xf3, 0x59, 0xb0, 0x5d, 0xba, 0x55, 0x64,
	0xa9, 0xd9, 0xd7, 0xb0, 0x14, 0x95, 0xa1, 0x06, 0x2f, 0x63, 0x23, 0x54, 0xa6, 0xfb, 0xd3, 0x01,
	0x58, 0xf5, 0x97, 0x1d, 0x11, 0xba, 0x5e, 0x87, 0xce, 0x48, 0x31, 0x73, 0x61, 0x27, 0x98, 0xcb,
	0x40, 0x67, 0x07, 0x58, 0x79, 0x55, 0xbd, 0x0e, 0xe5, 0x69, 0x6b, 0xd6, 0x8c, 0x0d, 0xa1, 0x4e,
	0x33, 0x4e, 0x73, 0xd3, 0x1a, 0x3e, 0x28, 0xb7, 0xf5, 0x98, 0xc2, 0x73, 0x5b, 0x1e, 0xd9, 0xd1,
	0xf6, 0x32, 0x94, 0x1d, 0x41, 0x3d, 0xb2, 0x13, 0xcf, 0x6b, 0x3d, 0xc7, 0xed, 0x0c, 0x77, 0xcb,
	0x9a, 0x93, 0xc0, 0x04, 0xe3, 0x65, 0x2a, 0xbc, 0x0c, 0x39, 0x46, 0xe0, 0xa8, 0xa6, 0x65, 0xa2,
	0x78, 0x35, 0x8e, 0xb7, 0x8b, 0xb9, 0x21, 0x7b, 0x3d, 0x72, 0xbe, 0xbe, 0x9d, 0x4a, 0x33, 0x5b,
	0x5c, 0xf4, 0x43, 0x8c, 0x07, 0xa5, 0xe7, 0xe6, 0xe6, 0x70, 0x8a, 0xd7, 0xde, 0xa1, 0xdf, 0x8e,
	0x73, 0xb1, 0x41, 0xaf, 0xd0, 0xcb, 0x3f, 0x01, 0x00, 0x00, 0xff, 0xff, 0x80, 0x4b, 0x7c, 0xd5,
	0x14, 0x05, 0x00, 0x00,
}
