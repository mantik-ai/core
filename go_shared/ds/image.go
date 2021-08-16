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
	"bytes"
	"encoding/json"
	"github.com/pkg/errors"
	"io"
)

type Image struct {
	Width      int                 `json:"width"`
	Height     int                 `json:"height"`
	Components OrderedComponentMap `json:"components"`
	// Image format: supported "plain", "png", "jpeg" (empty is the same as "plain" during decoding)
	Format string `json:"format,omitempty"`
}

type OrderedComponentMap []ImageComponentElement

type ImageComponentElement struct {
	Channel   ImageChannel
	Component ImageComponent
}

type ImageChannel string

const (
	Red   ImageChannel = "red"
	Green ImageChannel = "green"
	Blue  ImageChannel = "blue"
	Black ImageChannel = "black"
)

var channels = []ImageChannel{
	Red,
	Green,
	Blue,
	Black,
}

func (t *Image) UnmarshalJSON(i []byte) error {
	// creates its own type so that UnmarshallJson is not overridden anymore
	type ImageJson Image
	err := json.Unmarshal(i, (*ImageJson)(t))
	if err != nil {
		return err
	}
	// Setting default values
	if len(t.Format) == 0 {
		t.Format = "plain"
	}
	return nil
}

/* Create an image definition with a single channel and raw encoding. Convenience Constructor. */
func CreateSingleChannelRawImage(width int, height int, channel ImageChannel, dataType *FundamentalType) *Image {
	return &Image{
		width,
		height,
		[]ImageComponentElement{
			{channel, ImageComponent{Ref(dataType)}},
		},
		"plain",
	}
}

func (t *Image) IsFundamental() bool {
	return false
}

func (t *Image) TypeName() string {
	return "image"
}

// Return true, if this is a plain image.
func (t *Image) IsPlain() bool {
	return t.Format == "plain"
}

func findChannel(s string) (*ImageChannel, error) {
	for _, c := range channels {
		if s == (string)(c) {
			return &c, nil
		}
	}
	return nil, errors.Errorf("Channel not found %s", s)
}

type ImageComponent struct {
	ComponentType TypeReference `json:"componentType"`
}

// TODO: Remove Copy and Paste with NamedDataTypeMap
func (omap OrderedComponentMap) MarshalJSON() ([]byte, error) {
	var buf bytes.Buffer

	buf.WriteString("{")
	for i, kv := range omap {
		if i != 0 {
			buf.WriteString(",")
		}
		// marshal key
		key, err := json.Marshal(kv.Channel)
		if err != nil {
			return nil, err
		}
		buf.Write(key)
		buf.WriteString(":")
		// marshal value
		val, err := json.Marshal(kv.Component)
		if err != nil {
			return nil, err
		}
		buf.Write(val)
	}

	buf.WriteString("}")
	return buf.Bytes(), nil
}

// TODO: Remove Copy and Paste with NamedDataTypeMap
func (omap *OrderedComponentMap) UnmarshalJSON(data []byte) error {
	dec := json.NewDecoder(bytes.NewReader(data))
	dec.UseNumber()
	t, err := dec.Token()
	if err != nil {
		return err
	}
	if delim, ok := t.(json.Delim); !ok || delim != '{' {
		return errors.New("Expected {")
	}
	keyvalues := make([]ImageComponentElement, 0)

	for dec.More() {
		t, err = dec.Token()
		if err != nil {
			return err
		}
		key, ok := t.(string)
		if !ok {
			return errors.New("Expected string, got something else")
		}
		channel, err := findChannel(key)
		if err != nil {
			return err
		}

		var value ImageComponent
		err = dec.Decode(&value)
		if err != nil {
			return err
		}
		keyvalues = append(keyvalues, ImageComponentElement{*channel, value})
	}
	t, err = dec.Token()
	if delim, ok := t.(json.Delim); !ok || delim != '}' {
		return errors.New("Expected }")
	}
	t, err = dec.Token()
	if err != io.EOF {
		return errors.New("No data expected")
	}
	*omap = keyvalues
	return nil
}
