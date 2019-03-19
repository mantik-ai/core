package progressutil

import (
	"io"
	"time"
)

func CopyAndNotify(duration time.Duration, in io.Reader, out io.Writer, fn func(bytes int64, failed error, done bool)) (int64, error) {
	wrapped := MakeProgressReader(in)
	notifier := GeneratePeriodicCallback(duration, func() {
		fn(wrapped.Bytes(), nil, false)
	})
	bytes, err := io.Copy(out, wrapped)
	notifier.Stop()
	if err != nil {
		fn(wrapped.Bytes(), err, false)
	} else {
		fn(wrapped.Bytes(), nil, true)
	}
	return bytes, err
}
