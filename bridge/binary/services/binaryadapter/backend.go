package binaryadapter

import "gl.ambrosys.de/mantik/go_shared/serving"

type BinaryBackend struct {
}

func (t *BinaryBackend) LoadModel(payloadDir *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	return CreateBinaryExecutor(payloadDir, mantikHeader)
}
