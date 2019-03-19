package async

import (
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"testing"
	"time"
)

func TestParallelRunner(t *testing.T) {
	core := CreateAsyncCore()
	t.Run("Empty case", func(t *testing.T) {

		p := core.Parallel()
		assert.NoError(t, p.Result())
	})
	t.Run("Success case", func(t *testing.T) {
		p := core.Parallel()
		t0 := time.Now()
		p.Add(func() error {
			core.Sleep(15 * time.Millisecond)
			return nil
		})
		p.Add(func() error {
			core.Sleep(20 * time.Millisecond)
			return nil
		})
		result := p.Result()
		t1 := time.Now()
		assert.NoError(t, result)
		assert.WithinDuration(t, t1, t0, 30*time.Millisecond)
	})
	t.Run("Error case1", func(t *testing.T) {
		e := errors.New("Failed")
		p := core.Parallel()
		t0 := time.Now()
		p.Add(func() error {
			core.Sleep(15 * time.Millisecond)
			return e
		})
		p.Add(func() error {
			core.Sleep(20 * time.Millisecond)
			return nil
		})
		result := p.Result()
		t1 := time.Now()
		assert.Equal(t, e, result)
		assert.WithinDuration(t, t1, t0, 30*time.Millisecond)
	})
	t.Run("Error case2", func(t *testing.T) {
		e := errors.New("Failed")
		p := core.Parallel()
		t0 := time.Now()
		p.Add(func() error {
			core.Sleep(15 * time.Millisecond)
			return nil
		})
		p.Add(func() error {
			core.Sleep(20 * time.Millisecond)
			return e
		})
		result := p.Result()
		t1 := time.Now()
		assert.Equal(t, e, result)
		assert.WithinDuration(t, t1, t0, 30*time.Millisecond)
	})
	t.Run("Error case3", func(t *testing.T) {
		e := errors.New("Failed")
		e2 := errors.New("Other error")
		p := core.Parallel()
		t0 := time.Now()
		p.Add(func() error {
			core.Sleep(30 * time.Millisecond)
			return e
		})
		p.Add(func() error {
			core.Sleep(15 * time.Millisecond)
			return e2
		})
		result := p.Result()
		t1 := time.Now()
		assert.Equal(t, e2, result)
		assert.WithinDuration(t, t1, t0, 40*time.Millisecond)
	})
}
