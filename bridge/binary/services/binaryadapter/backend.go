package binaryadapter

import "gl.ambrosys.de/mantik/go_shared/serving"

type BinaryBackend struct {
}

func (t *BinaryBackend) LoadModel(payloadDir *string, mantikfile serving.Mantikfile) (serving.Executable, error) {
	return CreateBinaryExecutor(payloadDir, mantikfile)
}
