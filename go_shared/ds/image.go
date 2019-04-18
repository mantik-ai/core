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
	Format     *string             `json:"format,omitempty"`
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

func (t *Image) IsFundamental() bool {
	return false
}

func (t *Image) TypeName() string {
	return "image"
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

// TODO: Remove Copy and Paste with OrderedMap
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

// TODO: Remove Copy and Paste with OrderedMap
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
