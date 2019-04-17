package sidecar

import (
	"bytes"
	"coordinator/service/protocol"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/http"
	"net/url"
	"time"
)

type SideCar struct {
	server *protocol.SideCarServer
	*PlainSidecar
	shutdownWebservice  bool // if true, shutdown the webservice at the end
	webserviceReachable bool
}

func CreateSideCar(settings *protocol.Settings, rootUrl string, shutdownWebservice bool) (*SideCar, error) {
	log.Debugf("Starting side car for %s", rootUrl)
	var s SideCar
	var err error
	url, err := url.Parse(rootUrl)
	if err != nil {
		log.Errorf("Could not parse URL %s", rootUrl)
		return nil, err
	}
	s.server, err = protocol.CreateSideCarServer(settings, &s)
	if err != nil {
		log.Errorf("Could not create server %s", err)
		return nil, err
	}
	s.shutdownWebservice = shutdownWebservice
	s.PlainSidecar = CreateUrlSidecar(url, settings, s.blockUntilWebServiceReachable, s.server.Coordinator)
	return &s, nil
}

func (s *SideCar) Port() int {
	return s.server.Port()
}

// Starts the sidecar, blocking.
func (s *SideCar) Run() error {
	s.server.RunAsync()
	s.server.Ac.StartGoroutine(s.waitWebServiceReachable)

	err := s.server.Ac.WaitGoroutines()

	s.shutdownWebServiceIfEnabled()
	if err != nil {
		return err
	}

	log.Info("Sidecar done")
	return nil
}

// Force close the sidecar (for testcases)
func (s *SideCar) ForceQuit() {
	s.server.ForceQuit()
}

func (s *SideCar) waitWebServiceReachable() {
	if s.server.Settings.WaitForWebServiceReachable == 0 {
		log.Infof("Waiting for webservice disabled...")
		s.lock.Lock()
		defer s.lock.Unlock()
		s.webserviceReachable = true
		return
	}
	timeout := time.Now().Add(s.server.Settings.WaitForWebServiceReachable)
	trials := 0
	for {
		err := s.tryAccessWebService()
		if err == nil {
			log.Infof("URL %s reachable", s.url)
			s.lock.Lock()
			defer s.lock.Unlock()
			s.webserviceReachable = true
			return
		}
		if s.server.Ac.Context.Err() != nil {
			return
		}
		trials++
		if time.Now().After(timeout) {
			log.Warnf("Giving up accessing %s after %d trials in %s", s.url, trials, s.server.Settings.WaitForWebServiceReachable.String())
			log.Warnf("Last error %s", err.Error())
			s.server.Ac.Fail(errors.Wrapf(err, "Timeout accessing %s (%s)", s.url, s.server.Settings.WaitForCoordinatorTimeout.String()))
			return
		}
		select {
		case <-time.After(s.server.Settings.RetryTime):
		case <-s.server.Ac.Context.Done():
		}
	}
}

func (s *SideCar) tryAccessWebService() error {
	req, err := http.NewRequest(http.MethodGet, s.url.String(), nil)
	if err != nil {
		return err
	}
	req = req.WithContext(s.server.Ac.Context)
	head, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	// Accept 2xx Status as OK.
	if head.StatusCode < 200 || head.StatusCode >= 300 {
		log.Infof("Server not yet reachable, status %d", head.StatusCode)
		err = errors.Errorf("URL %s returned %d", s.url, head.StatusCode)
		return err
	}
	return nil
}

func (s *SideCar) shutdownWebServiceIfEnabled() {
	if s.shutdownWebservice {
		url, err := s.resolveDestination("/admin/quit")
		if err != nil {
			log.Errorf("Could not resolve /admin/quit Call %s", err.Error())
			return
		}
		log.Infof("Calling POST %s", url)
		response, err := http.Post(url, "", &bytes.Buffer{})
		if err != nil {
			log.Errorf("Calling /admin/quit failed: %s", err.Error())
		} else if response.StatusCode != 200 {
			log.Errorf("Calling /admin/quit returned non 200 %d", response.StatusCode)
		}
	}
}

func (s *SideCar) blockUntilWebServiceReachable() error {
	return s.server.Ac.BlockUntil(s.server.Settings.WaitForWebServiceReachable, func() bool {
		s.lock.Lock()
		defer s.lock.Unlock()
		return s.webserviceReachable
	})
}
