package async

import (
	"errors"
	"github.com/stretchr/testify/assert"
	"sync"
	"testing"
	"time"
)

func TestWaitConSuc(t *testing.T) {
	wc := NewWaitCon()

	var wg sync.WaitGroup
	wg.Add(2)
	var r error
	go func() {
		defer wg.Done()
		r = wc.Wait()
	}()
	go func() {
		defer wg.Done()
		time.Sleep(50 * time.Millisecond)
		wc.Finish(nil)
	}()
	wg.Wait()
	assert.NoError(t, r)
	assert.NoError(t, wc.Wait()) // idempotent once it is in a state
}

func TestWaitConErr(t *testing.T) {
	wc := NewWaitCon()

	err := errors.New("Boom")

	var wg sync.WaitGroup
	wg.Add(2)
	var r error
	go func() {
		defer wg.Done()
		r = wc.Wait()
	}()
	go func() {
		defer wg.Done()
		time.Sleep(50 * time.Millisecond)
		wc.Finish(err)
	}()
	wg.Wait()
	assert.Equal(t, err, r)
	assert.Equal(t, err, wc.Wait()) // idempotent once it is in a state
}

func TestWaitCon_TimedWait(t *testing.T) {
	wc := NewWaitCon()
	result := wc.WaitFor(100 * time.Millisecond)
	assert.Equal(t, TimeoutError, result)
}

func TestWaitCon_TimedWaitOk(t *testing.T) {
	wc := NewWaitCon()
	time.AfterFunc(50*time.Millisecond, func() {
		wc.Finish(nil)
	})
	result := wc.WaitFor(100 * time.Millisecond)
	assert.NoError(t, result)
}

func TestWaitCon_AlradyOk(t *testing.T) {
	wc := NewWaitCon()
	err := errors.New("Boom")
	wc.Finish(err)
	result := wc.WaitFor(1 * time.Second)
	assert.Equal(t, err, result)
}
