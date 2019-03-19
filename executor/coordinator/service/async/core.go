package async

import (
	"context"
	"errors"
	"net"
	"sync"
	"time"
)

var TimeoutError = errors.New("Timeout")

type AsyncCoreState int

const (
	Running AsyncCoreState = iota
	// Terminal states
	Quit
	Canceled
	Failed
)

// Contains a lot of helpers for asyncing stuff, especially with handling cancellation behaviour
// and error code handling
type AsyncCore struct {
	Context      context.Context
	cancelFn     context.CancelFunc
	cancelReason string
	mutex        sync.Mutex
	wg           sync.WaitGroup
	failure      error
	state        AsyncCoreState
}

// Creates async core
func CreateAsyncCore() *AsyncCore {
	mainContext, cancelFn := context.WithCancel(context.Background())
	return &AsyncCore{
		Context:  mainContext,
		cancelFn: cancelFn,
		state:    Running,
	}
}

// Cancel the core
func (a *AsyncCore) Cancel(reason string) {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	a.cancelReason = reason
	a.cancelFn()
	if a.state == Running {
		a.state = Canceled
	}
}

// Let the whole process fail
func (a *AsyncCore) Fail(err error) {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	a.failure = err
	a.cancelFn()
	if a.state == Running {
		a.state = Failed
	}
}

// Let the whole process quit
func (a *AsyncCore) Quit() {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	a.cancelReason = "quit"
	a.cancelFn()
	if a.state == Running {
		a.state = Quit
	}
}

func (a *AsyncCore) State() AsyncCoreState {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	return a.state
}

// Cancelable listen call
func (a *AsyncCore) Listen(network string, address string) (net.Listener, error) {
	var lc net.ListenConfig
	return lc.Listen(a.Context, network, address)
}

// Cancelable accept call
func (a *AsyncCore) Accept(listener net.Listener) (net.Conn, error) {
	ec := make(chan error, 1)
	cc := make(chan net.Conn, 1)
	go func() {
		c, err := listener.Accept()
		if err != nil {
			ec <- err
		} else {
			cc <- c
		}
	}()

	select {
	case e := <-ec:
		return nil, e
	case c := <-cc:
		return c, nil
	case <-a.Context.Done():
		return nil, context.Canceled
	}
}

// Sleeps until for duration, or if it is canceled.
func (a *AsyncCore) Sleep(duration time.Duration) {
	select {
	case <-a.Context.Done():
	case <-time.After(duration):
	}
}

// Set a timeout, which will not be triggered if the job core is canceled.
func (a *AsyncCore) SetTimeout(duration time.Duration, f func()) {
	timeoutChannel := time.After(duration)
	a.StartGoroutine(func() {
		select {
		case <-timeoutChannel:
			f()
		case <-a.Context.Done():
			return
		}
	})
}

// Start some process and put it into the wait list
func (a *AsyncCore) StartGoroutine(f func()) {
	a.wg.Add(1)
	go func() {
		defer a.wg.Done()
		f()
	}()
}

// Wait for go routines and returns the Result
func (a *AsyncCore) WaitGoroutines() error {
	a.wg.Wait()
	return a.Result()
}

/** Return the current result of the Core (without blocking). */
func (a *AsyncCore) Result() error {
	a.mutex.Lock()
	defer a.mutex.Unlock()
	switch a.state {
	case Failed:
		return a.failure
	case Canceled:
		return context.Canceled
	default:
		return nil
	}
}

// Return true if the core is in a terminal state
// Routines should stop now.
func (a *AsyncCore) IsTerminalState() bool {
	return a.State() != Running
}

// Periodically check a function until it returns true
// Returns TimeoutError on timeout
// Or nil on no error
// Or context.Canceled if the core is canceled.
func (a *AsyncCore) BlockUntil(duration time.Duration, check func() bool) error {
	timeout := time.Now().Add(duration)
	for {
		v := check()
		if v {
			return nil
		}
		if a.IsTerminalState() {
			return context.Canceled
		}
		a.Sleep(time.Millisecond * 10)
		c := time.Now()
		if c.After(timeout) {
			return TimeoutError
		}
	}
}

// Returns the internal quit error or a given error if no internal error is set.
func (a *AsyncCore) CombineErr(err error) error {
	internalErr := a.Result()
	if internalErr != nil {
		return internalErr
	} else {
		return err
	}
}
