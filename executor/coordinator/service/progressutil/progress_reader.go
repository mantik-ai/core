package progressutil

import (
	"io"
	"sync"
)

// Wraps a reader to see how many bytes have been read
type ProgressReader struct {
	r     io.Reader
	bytes int64
	lock  sync.Mutex
	ready bool
}

func MakeProgressReader(reader io.Reader) *ProgressReader {
	var r ProgressReader
	r.r = reader
	return &r
}

func (w *ProgressReader) Read(p []byte) (n int, err error) {
	n, err = w.r.Read(p)
	w.lock.Lock()
	defer w.lock.Unlock()
	if err == io.EOF {
		w.ready = true
	}
	w.bytes += int64(n)
	return
}

func (w *ProgressReader) BytesAndReady() (int64, bool) {
	w.lock.Lock()
	defer w.lock.Unlock()
	return w.bytes, w.ready
}

func (w *ProgressReader) Bytes() int64 {
	w.lock.Lock()
	defer w.lock.Unlock()
	return w.bytes
}
