package tfadapter

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
)

type TensorflowBackend struct {
}

func (t *TensorflowBackend) LoadModel(payloadDir *string, mantikfile serving.Mantikfile) (serving.Executable, error) {
	if payloadDir == nil {
		return nil, errors.New("Payload required")
	}
	model, err := LoadModel(*payloadDir)
	if err != nil {
		return nil, err
	}
	return model, err
}
