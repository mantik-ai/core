package sidecar

import (
	"context"
	"coordinator/service/protocol"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/http"
	"time"
)

/** Wait until the given URL is reachable. */
func HttpWaitReachable(context context.Context, url string, settings *protocol.Settings) error {
	if settings.WaitForWebServiceReachable == 0 {
		log.Infof("Waiting for webservice %s disabled...", url)
		return nil
	}
	timeout := time.Now().Add(settings.WaitForWebServiceReachable)
	trials := 0
	for {
		err := tryAccessWebService(context, url)
		if err == nil {
			log.Infof("URL %s reachable", url)
			return nil
		}
		if context.Err() != nil {
			return nil
		}
		trials++
		if time.Now().After(timeout) {
			log.Warnf("Giving up accessing %s after %d trials in %s", url, trials, settings.WaitForWebServiceReachable.String())
			log.Warnf("Last error %s", err.Error())
			return errors.Wrapf(err, "Timeout accessing %s (%s)", url, settings.WaitForCoordinatorTimeout.String())
		}
		select {
		case <-time.After(settings.RetryTime):
		case <-context.Done():
		}
	}
}

func tryAccessWebService(context context.Context, url string) error {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if err != nil {
		return err
	}
	req = req.WithContext(context)
	head, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	// Accept 2xx Status as OK.
	if head.StatusCode < 200 || head.StatusCode >= 300 {
		log.Infof("Server %s not yet reachable, status %d", url, head.StatusCode)
		err = errors.Errorf("URL %s returned %d", url, head.StatusCode)
		return err
	}
	return nil
}
