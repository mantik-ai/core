package progressutil

import "time"

/* Automatically calls periodic wise */
type PeriodicCallback struct {
	ticker *time.Ticker
	quit   chan struct{}
}

/* Stops calling a PeriodicCallback. */
func (n *PeriodicCallback) Stop() {
	n.ticker.Stop()
	close(n.quit)
}

/** Generate cancellable function which is called every second. */
func GeneratePeriodicCallback(duration time.Duration, f func()) *PeriodicCallback {
	var n PeriodicCallback
	n.ticker = time.NewTicker(duration)
	n.quit = make(chan struct{})
	go func() {
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
