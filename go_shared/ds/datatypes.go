/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ds

import (
	"encoding/json"
	"log"
	"reflect"
)

type DataType interface {

	// If true, the type is fundamental
	IsFundamental() bool

	// Returns the type name of the type (for fundamental types, this is the name)
	// Used for encoding
	TypeName() string
}

func DataTypeEquality(a DataType, b DataType) bool {
	return reflect.DeepEqual(a, b)
}

type FundamentalType struct {
	GoType reflect.Type
	name   string
}

var Int32 = &FundamentalType{
	// Seems like the only way to get the reflected type: https://stackoverflow.com/questions/40879748/golang-reflect-get-type-representation-from-name?rq=1
	GoType: reflect.TypeOf((*int32)(nil)).Elem(),
	name:   "int32",
}

var Uint32 = &FundamentalType{
	GoType: reflect.TypeOf((*uint32)(nil)).Elem(),
	name:   "uint32",
}

var Int64 = &FundamentalType{
	GoType: reflect.TypeOf((*int64)(nil)).Elem(),
	name:   "int64",
}

var Uint64 = &FundamentalType{
	GoType: reflect.TypeOf((*uint64)(nil)).Elem(),
	name:   "uint64",
}

var Uint8 = &FundamentalType{
	GoType: reflect.TypeOf((*uint8)(nil)).Elem(),
	name:   "uint8",
}

var Int8 = &FundamentalType{
	GoType: reflect.TypeOf((*int8)(nil)).Elem(),
	name:   "int8",
}

var Float32 = &FundamentalType{
	GoType: reflect.TypeOf((*float32)(nil)).Elem(),
	name:   "float32",
}

var Float64 = &FundamentalType{
	GoType: reflect.TypeOf((*float64)(nil)).Elem(),
	name:   "float64",
}

var Bool = &FundamentalType{
	GoType: reflect.TypeOf((*bool)(nil)).Elem(),
	name:   "bool",
}

var String = &FundamentalType{
	GoType: reflect.TypeOf((*string)(nil)).Elem(),
	name:   "string",
}

var Void = &FundamentalType{
	GoType: reflect.TypeOf((*interface{})(nil)).Elem(),
	name:   "void",
}

func (f *FundamentalType) IsFundamental() bool {
	return true
}

func (f *FundamentalType) TypeName() string {
	return f.name
}

func (f *FundamentalType) MarshalJSON() ([]byte, error) {
	return json.Marshal(f.name)
}

var fundamentalTypes = []*FundamentalType{
	Int8,
	Uint8,
	Int32,
	Uint32,
	Int64,
	Uint64,
	Float32,
	Float64,
	String,
	Bool,
	Void,
}

func FromJson(bytes []byte) (DataType, error) {
	r := TypeReference{}
	err := json.Unmarshal(bytes, &r)
	if err != nil {
		return nil, err
	}
	return r.Underlying, nil
}

func FromJsonString(s string) (DataType, error) {
	return FromJson(([]byte)(s))
}

func FromJsonStringOrPanic(s string) DataType {
	d, e := FromJsonString(s)
	if e != nil {
		log.Panicf("Could not convert JSON string to DataType %s", e.Error())
	}
	return d
}

func FromJsonStringOrPanicRef(s string) TypeReference {
	return Ref(FromJsonStringOrPanic(s))
}

func ToJsonString(dataType DataType) string {
	r := TypeReference{dataType}
	v, err := json.Marshal(r)
	if err != nil {
		println("Something went wrong on marshalling", err.Error())
	}
	return string(v)
}

type Nullable struct {
	Underlying TypeReference `json:"underlying"`
}

func (n *Nullable) IsFundamental() bool {
	return false
}

func (n *Nullable) TypeName() string {
	return "nullable"
}

type Struct struct {
	Fields OrderedMap `json:"fields"`
}

func (t *Struct) GetField(name string) DataType {
	for _, value := range t.Fields {
		if value.Name == name {
			return value.SubType.Underlying
		}
	}
	return nil
}

func (t *Struct) IndexOfField(name string) int {
	for i, value := range t.Fields {
		if value.Name == name {
			return i
		}
	}
	return -1
}

func (t *Struct) Arity() int {
	return len(t.Fields)
}

func (t *Struct) IsFundamental() bool {
	return false
}

func (t *Struct) TypeName() string {
	return "struct"
}

type Array struct {
	Underlying TypeReference `json:"underlying"`
}

func (a *Array) IsFundamental() bool {
	return false
}

func (a *Array) TypeName() string {
	return "array"
}
