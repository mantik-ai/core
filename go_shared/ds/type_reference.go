package ds

import (
	"encoding/json"
	"github.com/pkg/errors"
)

/* References another type. Responsible for JSON-Deserialization and Deserialization and type-name-Adding. */
type TypeReference struct {
	Underlying DataType
}

type discriminator struct {
	Type *string `json:"type"`
}

/* Shortcut for creating references. */
func Ref(dataType DataType) TypeReference {
	return TypeReference{Underlying: dataType}
}

/** Like Ref, but returns a pointer. */
func Refp(dataType DataType) *TypeReference {
	x := Ref(dataType)
	return &x
}

func (t TypeReference) MarshalJSON() ([]byte, error) {
	if t.Underlying.IsFundamental() {
		result := "\"" + t.Underlying.TypeName() + "\""
		return []byte(result), nil
	} else {
		subEncoded, err := json.Marshal(t.Underlying)
		if err != nil {
			return nil, err
		}
		if len(subEncoded) == 0 || subEncoded[0] != '{' {
			return nil, errors.Errorf("Unexpected encoding %s", (string)(subEncoded))
		}
		prefix := []byte("{\"type\":\"" + t.Underlying.TypeName() + "\",")
		result := append(prefix, subEncoded[1:]...)
		return result, nil
	}
}

/** Encodes the type to json, if it fails it will return an empty string.
(which should not happen)*/
func (t TypeReference) ToJsonString() string {
	return ToJsonString(t.Underlying)
}

func (t *TypeReference) UnmarshalJSON(bytes []byte) error {
	if len(bytes) <= 0 {
		return errors.New("Empty JSON")
	}
	if bytes[0] == '"' {
		// Fundamental type
		var s string
		err := json.Unmarshal(bytes, &s)
		if err == nil {
			for _, v := range fundamentalTypes {
				if v.name == s {
					t.Underlying = v
					return nil
				}
			}
			return errors.New("Unknown fundamental type " + s)
		}
	}

	d := discriminator{}
	err := json.Unmarshal(bytes, &d)
	if err != nil {
		return err
	}
	if d.Type == nil || *d.Type == "tabular" {
		tabular := TabularData{}
		err := json.Unmarshal(bytes, &tabular)
		if err != nil {
			return err
		}
		if len(tabular.Columns) == 0 {
			return errors.New("No support for tables without columns")
		}
		t.Underlying = &tabular
		return nil
	}
	if *d.Type == "image" {
		image := Image{}
		err := json.Unmarshal(bytes, &image)
		if err != nil {
			return err
		}
		t.Underlying = &image
		return nil
	}
	if *d.Type == "tensor" {
		tensor := Tensor{}
		err := json.Unmarshal(bytes, &tensor)
		if err != nil {
			return err
		}
		t.Underlying = &tensor
		return nil
	}
	return errors.New("Not implemented " + *d.Type)
}
