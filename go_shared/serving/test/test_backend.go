package test

import "gl.ambrosys.de/mantik/go_shared/serving"

/* A Backend for testing. */
type TestBackend struct {
	Executable     serving.Executable
	Instantiations []TestBackendInstatiation
	Closed         bool
}

type TestBackendInstatiation struct {
	MantikHeader serving.MantikHeader
	PayloadDir   *string
}

func NewTestBackend(executable serving.Executable) *TestBackend {
	return &TestBackend{
		Executable: executable,
	}
}

func (t *TestBackend) Shutdown() {
	t.Closed = true
}

func (t *TestBackend) LoadModel(payloadDir *string, mantikHeader serving.MantikHeader) (serving.Executable, error) {
	t.Instantiations = append(t.Instantiations, TestBackendInstatiation{
		MantikHeader: mantikHeader,
		PayloadDir:   payloadDir,
	})
	return t.Executable, nil
}
