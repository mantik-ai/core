package natural

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

type naturalEncoder struct {
	backend               serializer.SerializingBackend
	rootElementSerializer ElementSerializer
}

func (n naturalEncoder) Write(row element.Element) error {
	err := n.backend.NextRow()
	if err != nil {
		return err
	}
	return n.rootElementSerializer.Write(n.backend, row)
}

func (n naturalEncoder) Close() error {
	err := n.backend.Finish()
	if err != nil {
		return err
	}
	return n.backend.Flush()
}

/*
Create an encoder for natural data format.
*/
func CreateEncoder(format ds.DataType, backend serializer.SerializingBackend) (element.StreamWriter, error) {
	rootElementSerializer, err := LookupRootElementSerializer(format)
	if err != nil {
		return nil, errors.Wrap(err, "Could not create Root element serializer")
	}

	header := serializer.Header{ds.Ref(format)}
	err = backend.EncodeHeader(&header)

	if err != nil {
		return nil, err
	}

	_, isTabular := format.(*ds.TabularData)
	if isTabular {
		err = backend.StartTabularValues()
		if err != nil {
			return nil, err
		}
	}

	result := naturalEncoder{
		backend,
		rootElementSerializer,
	}
	return result, nil
}
