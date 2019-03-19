package async

import "sync"

// Helper for running multiple parallel routines and wait for the first error
type ParallelRunner struct {
	wg     sync.WaitGroup
	mux    sync.Mutex
	result error
	done   bool
}

// Start the parallel runner
func (a *AsyncCore) Parallel() *ParallelRunner {
	return &ParallelRunner{}
}

func (r *ParallelRunner) Add(f func() error) {
	if r.done {
		panic("Do not reuse")
	}
	r.wg.Add(1)
	go func() {
		defer r.wg.Done()
		res := f()
		r.mux.Lock()
		defer r.mux.Unlock()
		if res != nil && r.result == nil {
			r.result = res
		}
	}()
}

// Returns the first error which happend
func (r *ParallelRunner) Result() error {
	r.wg.Wait()
	r.done = true
	return r.result
}
