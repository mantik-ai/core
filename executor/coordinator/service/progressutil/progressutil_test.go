package progressutil

import (
	"bytes"
	"errors"
	"github.com/stretchr/testify/assert"
	"io"
	"testing"
	"time"
)

type dummySource struct {
	pending         int
	delay           time.Duration
	makeErrorOnQuit error
}

func (d *dummySource) Read(p []byte) (n int, err error) {
	if d.pending <= 0 {
		if d.makeErrorOnQuit != nil {
			return 0, d.makeErrorOnQuit
		} else {
			return 0, io.EOF
		}
	}
	d.pending -= 1
	time.Sleep(d.delay)
	p[0] = 'A'
	p[1] = 'B'
	return 2, nil
}

func TestSimpleNotification(t *testing.T) {
	source1 := dummySource{
		20, 20 * time.Millisecond, nil,
	}
	var result bytes.Buffer
	calls := 0
	foundDone := false
	lastBytes := int64(0)
	bytes, err := CopyAndNotify(40*time.Millisecond, &source1, &result, func(bytes int64, failed error, done bool) {
		calls++
		assert.NoError(t, failed)
		assert.True(t, bytes >= lastBytes)
		lastBytes = bytes
		if done {
			// done must be the last one
			assert.False(t, foundDone)
			foundDone = true
		} else {
			assert.False(t, foundDone)
		}
	})
	assert.NoError(t, err)
	assert.True(t, calls > 5 && calls < 14)
	assert.Equal(t, int64(40), bytes)
	assert.Equal(t, bytes, lastBytes)
	assert.True(t, foundDone)
}

func TestFailingNotification(t *testing.T) {
	// Should take around 1 second to read
	fakeErr := errors.New("BOOM!")
	source1 := dummySource{
		20, 5 * time.Millisecond, fakeErr,
	}
	var result bytes.Buffer
	calls := 0
	lastBytes := int64(0)
	var lastErr error
	bytes, err := CopyAndNotify(10*time.Millisecond, &source1, &result, func(bytes int64, failed error, done bool) {
		calls++
		assert.True(t, bytes >= lastBytes)
		lastBytes = bytes
		assert.False(t, done)
		if failed != nil {
			assert.NoError(t, lastErr) // error should come up once.
			lastErr = failed
		}
	})
	assert.Equal(t, fakeErr, err)
	assert.True(t, calls > 5 && calls < 14)
	assert.Equal(t, int64(40), bytes)
	assert.Equal(t, bytes, lastBytes)
	assert.Equal(t, fakeErr, lastErr)
}
