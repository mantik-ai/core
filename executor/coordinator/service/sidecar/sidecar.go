package sidecar

import (
	"bytes"
	"coordinator/service/protocol"
	"coordinator/service/streamer"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net/http"
	"net/url"
	"sync"
	"time"
)

type SideCar struct {
	server              *protocol.SideCarServer
	url                 *url.URL
	getStreams          []*streamer.HttpGetSource
	postSinks           []*streamer.HttpPostSink
	postTransformations []*streamer.HttpPostTransformation
	lock                sync.Mutex
	shutdownWebservice  bool // if true, shutdown the webservice at the end
	webserviceReachable bool
}

func CreateSideCar(settings *protocol.Settings, rootUrl string, shutdownWebservice bool) (*SideCar, error) {
	log.Debugf("Starting side car for %s", rootUrl)
	var s SideCar
	var err error
	s.url, err = url.Parse(rootUrl)
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
	req, err := http.NewRequest(http.MethodHead, s.url.String(), nil)
	if err != nil {
		return err
	}
	req = req.WithContext(s.server.Ac.Context)
	head, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	// All internal server errors are used as "Server not ready" message
	// Although 503 should be sufficient.
	// (We could also restrict to 2xx responses, but this would force all bridges
	//  to return something useful on the root path)
	if head.StatusCode >= 500 {
		err = errors.Errorf("URL %s returned %d", s.url, head.StatusCode)
		return err
	}
	return nil
}

func (s *SideCar) shutdownWebServiceIfEnabled() {
	if s.shutdownWebservice {
		url, err := s.resolveResource("/admin/quit")
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

/** Resolve the URL of a Resource. */
func (s *SideCar) resolveResource(resource string) (string, error) {
	u, err := url.Parse(resource)
	if err != nil {
		return "", err
	}
	full := s.url.ResolveReference(u)
	return full.String(), nil
}

func (s *SideCar) RequestStream(req *protocol.RequestStream, res *protocol.RequestStreamResponse) error {
	err := s.blockUntilWebServiceReachable()
	if err != nil {
		return err
	}

	fullUrl, err := s.resolveResource(req.Resource)
	if err != nil {
		return err
	}
	streamer, err := streamer.CreateHttpGetSource(fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id), s.server.Settings.HttpGetRetryTime)
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added a new streamer for %s to port %d", fullUrl, streamer.Port)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.getStreams = append(s.getStreams, streamer)
	res.Port = streamer.Port
	return nil
}

func (s *SideCar) makeStreamNotifyFn(id string) streamer.StreamNotifyFn {
	return func(reqId int, in int64, out int64, inFailure error, done bool) {
		coordinator, err := s.server.Coordinator()
		if err != nil {
			log.Warn("Could not get coordinator?!")
			return
		}
		var s protocol.StatusUpdate
		var r protocol.StatusUpdateResponse
		s.Id = id
		// name and challenge is automatially added
		s.Ingress = in
		s.Outgress = out
		s.Done = done
		if inFailure != nil {
			s.Error = inFailure.Error()
		}
		err = coordinator.StatusUpdate(&s, &r)
		if err != nil {
			log.Warnf("Could not send status update to coordinator %s", err.Error())
		}
	}
}

func (s *SideCar) RequestTransfer(req *protocol.RequestTransfer, res *protocol.RequestTransferResponse) error {
	err := s.blockUntilWebServiceReachable()
	if err != nil {
		return err
	}

	fullUrl, err := s.resolveResource(req.Resource)
	if err != nil {
		return err
	}
	sink, err := streamer.CreateHttpPostSink(req.Source, fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id))
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added new sink from %s to %s", req.Source, fullUrl)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.postSinks = append(s.postSinks, sink)
	// no extra values to return
	return nil
}

func (s *SideCar) RequestTransformation(req *protocol.RequestTransformation, res *protocol.RequestTransformationResponse) error {
	err := s.blockUntilWebServiceReachable()
	if err != nil {
		return err
	}
	fullUrl, err := s.resolveResource(req.Resource)
	if err != nil {
		return err
	}
	transformation, err := streamer.CreateHttpPostTransformation(req.Source, fullUrl, req.ContentType, s.makeStreamNotifyFn(req.Id))
	if err != nil {
		res.Error = err.Error()
		return nil
	}
	log.Infof("Added new transformation from %s via %s to %d", req.Source, fullUrl, transformation.Port)
	s.lock.Lock()
	defer s.lock.Unlock()
	s.postTransformations = append(s.postTransformations, transformation)
	res.Port = transformation.Port
	return nil
}

func (s *SideCar) blockUntilWebServiceReachable() error {
	return s.server.Ac.BlockUntil(s.server.Settings.WaitForWebServiceReachable, func() bool {
		s.lock.Lock()
		defer s.lock.Unlock()
		return s.webserviceReachable
	})
}
