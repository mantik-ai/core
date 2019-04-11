package progressutil

import (
	"sync"
	"time"
)

/* Automatically calls periodic wise */
type PeriodicCallback struct {
	ticker *time.Ticker
	quit   chan struct{}
	wg     sync.WaitGroup
}

/* Stops calling a PeriodicCallback. Waits until all callbacks are done. */
func (n *PeriodicCallback) Stop() {
	n.ticker.Stop()
	close(n.quit)
	n.wg.Wait()
}

/** Generate cancellable function which is called every second. */
func GeneratePeriodicCallback(duration time.Duration, f func()) *PeriodicCallback {
	var n PeriodicCallback
	n.ticker = time.NewTicker(duration)
	n.quit = make(chan struct{})
	n.wg.Add(1)
	go func() {
		defer n.wg.Done()
		for {
			select {
			case <-n.ticker.C:
				f()
			case <-n.quit:
				n.ticker.Stop()
				return
			}
		}
	}()
	return &n
}
