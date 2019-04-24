package tfadapter

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"path"
)

type TensorflowBackend struct {
}

func (t *TensorflowBackend) LoadModel(directory string, mantikfile serving.Mantikfile) (serving.Executable, error) {
	if mantikfile.Directory() == nil {
		return nil, errors.New("Directory required")
	}
	fullDir := path.Join(directory, *mantikfile.Directory())
	model, err := LoadModel(fullDir)
	if err != nil {
		return nil, err
	}
	return model, err
}
