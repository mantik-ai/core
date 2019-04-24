package binaryadapter

import "gl.ambrosys.de/mantik/go_shared/serving"

type BinaryBackend struct {
}

func (t *BinaryBackend) LoadModel(directory string, mantikfile serving.Mantikfile) (serving.Executable, error) {
	return CreateBinaryExecutor(directory)
}
