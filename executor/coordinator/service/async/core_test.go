package async

import (
	"context"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
	"time"
)

func TestAsyncSleep(t *testing.T) {
	core := CreateAsyncCore()
	var wg sync.WaitGroup
	wg.Add(1)
	go func() {
		defer wg.Done()
		core.Sleep(time.Second)
	}()
	// core.Quit()
	t0 := time.Now()
	core.Cancel("boom")
	wg.Wait()
	t1 := time.Now()
	assert.WithinDuration(t, t1, t0, time.Millisecond*10)
}

func TestCancelableListening(t *testing.T) {
	core := CreateAsyncCore()
	l, err := core.Listen("tcp", "localhost:0")
	assert.NoError(t, err)
	var wg sync.WaitGroup
	wg.Add(1)
	var lastErr error
	go func() {
		defer wg.Done()
		_, lastErr = core.Accept(l)
	}()
	// core.Quit()
	core.Cancel("boom")
	wg.Wait()
	assert.Equal(t, context.Canceled, lastErr)
}

func TestWait(t *testing.T) {
	core := CreateAsyncCore()
	core.StartGoroutine(func() {
		core.Sleep(time.Millisecond * 20)
	})
	core.StartGoroutine(func() {
		core.Sleep(time.Millisecond * 20)
	})
	t0 := time.Now()
	core.WaitGoroutines()
	t1 := time.Now()
	assert.WithinDuration(t, t1, t0, 30*time.Millisecond)
}

func TestWaitCancellation(t *testing.T) {
	core := CreateAsyncCore()
	core.StartGoroutine(func() {
		core.Sleep(time.Millisecond * 10)
	})
	core.StartGoroutine(func() {
		core.Sleep(time.Millisecond * 10)
	})
	core.Quit()
	t0 := time.Now()
	core.WaitGoroutines()
	t1 := time.Now()
	assert.WithinDuration(t, t1, t0, 5*time.Millisecond)
}

func TestSetTimeout(t *testing.T) {
	core := CreateAsyncCore()
	var called time.Time
	core.SetTimeout(10*time.Millisecond, func() {
		called = time.Now()
	})
	t0 := time.Now()
	core.WaitGoroutines()
	assert.WithinDuration(t, t0, called, 20*time.Millisecond)
	assert.True(t, called.Sub(t0).Seconds() > 0.005)
}

func TestSetTimeoutCancellation(t *testing.T) {
	core := CreateAsyncCore()
	var called *time.Time
	core.SetTimeout(100*time.Millisecond, func() {
		x := time.Now()
		called = &x
	})
	t0 := time.Now()
	core.Quit()
	core.WaitGoroutines()
	t1 := time.Now()
	assert.WithinDuration(t, t1, t0, 5*time.Millisecond)
	assert.Nil(t, called)
}

func TestBlockUtil(t *testing.T) {
	core := CreateAsyncCore()

	t.Run("timeout case", func(t *testing.T) {
		err := core.BlockUntil(20*time.Millisecond, func() bool {
			return false
		})
		assert.Equal(t, TimeoutError, err)
	})

	t.Run("success case", func(t *testing.T) {
		var v = false
		core.SetTimeout(10*time.Millisecond, func() {
			v = true
		})
		t0 := time.Now()
		err := core.BlockUntil(time.Second, func() bool {
			return v
		})
		assert.NoError(t, err)
		t1 := time.Now()
		assert.WithinDuration(t, t1, t0, 40*time.Millisecond)
	})

	t.Run("cancellation case", func(t *testing.T) {
		core.SetTimeout(10*time.Millisecond, func() {
			core.Quit()
		})
		t0 := time.Now()
		err := core.BlockUntil(time.Second, func() bool {
			return false
		})
		t1 := time.Now()
		assert.Equal(t, context.Canceled, err)
		assert.WithinDuration(t, t1, t0, 40*time.Millisecond)
	})
}

func TestAsyncCore_CombineErr(t *testing.T) {
	myErr := errors.New("Boom")

	t.Run("Default Case", func(t *testing.T) {
		core := CreateAsyncCore()
		assert.Equal(t, myErr, core.CombineErr(myErr))
		core.Quit()
	})

	t.Run("Quit case", func(t *testing.T) {
		core := CreateAsyncCore()
		core.Quit()
		assert.Equal(t, myErr, core.CombineErr(myErr))
	})
	t.Run("Cancellation case", func(t *testing.T) {
		core := CreateAsyncCore()
		core.Cancel("Boom")
		assert.Equal(t, context.Canceled, core.CombineErr(myErr))
	})
	t.Run("Failure case", func(t *testing.T) {
		core := CreateAsyncCore()
		f := errors.New("Fail")
		core.Fail(f)
		assert.Equal(t, f, core.CombineErr(myErr))
	})

}
