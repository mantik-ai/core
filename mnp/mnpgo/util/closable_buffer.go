package util

import "bytes"

/* A Buffer which also has the Close()-Call from WriteCloser. */
type CloseableBuffer struct {
	bytes.Buffer
	Closed bool
}

func NewClosableBuffer() *CloseableBuffer {
	return &CloseableBuffer{}
}

func (c *CloseableBuffer) Close() error {
	c.Closed = true
	return nil
}
