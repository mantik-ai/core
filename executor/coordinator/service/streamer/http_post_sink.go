package streamer

import (
	"coordinator/service/progressutil"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net"
	"net/http"
)

/* A sink is an active process, which copies data from a TCP url to a HTTP Post sink. */
type HttpPostSink struct {
	from        string
	url         string
	fn          StreamNotifyFn
	contentType *string
	done        chan struct{}
}

func CreateHttpPostSink(from string, url string, contentType *string, fn StreamNotifyFn) (*HttpPostSink, error) {
	sink := HttpPostSink{
		from, url, fn, contentType, make(chan struct{}, 0),
	}
	log.Debugf("Created sink process from %s to %s", from, url)
	go sink.run()
	return &sink, nil
}

func (s *HttpPostSink) run() {
	conn, err := net.Dial("tcp", s.from)
	defer conn.Close()
	defer s.finish()
	if err != nil {
		log.Warnf("Could not request %s", s.from)
		s.fn(0, 0, 0, err, false)
		return
	}
	wrappedConnection := progressutil.MakeProgressReader(conn)
	notifier := progressutil.GeneratePeriodicCallback(StreamNotificationDelay, func() {
		s.fn(0, wrappedConnection.Bytes(), 0, nil, false)
	})
	req, err := http.NewRequest(http.MethodPost, s.url, wrappedConnection)
	if err != nil {
		log.Warnf("Could not create post request to %s, %s", s.url, err.Error())
		notifier.Stop()
		s.fn(0, 0, 0, err, false)
		return
	}
	if s.contentType != nil {
		req.Header.Add(HttpContentType, *s.contentType)
	}
	response, err := http.DefaultClient.Do(req)
	byteCount, isReady := wrappedConnection.BytesAndReady()
	notifier.Stop()
	if err != nil {
		log.Warnf("Could not execute request %s", err.Error())
		s.fn(0, byteCount, 0, err, false)
		return
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		log.Warnf("Received bad status code %d on HTTP POST %s", response.StatusCode, s.url)
		err = errors.Errorf("Received status code %d", response.StatusCode)
		s.fn(0, byteCount, 0, err, false)
		return
	}
	if !isReady {
		// can this happen in practice?
		log.Warnf("Receiver closed after receiving %d bytes but reported no error", byteCount)
	}
	s.fn(0, byteCount, 0, nil, true)
}

func (s *HttpPostSink) finish() {
	s.done <- struct{}{}
}

func (s *HttpPostSink) Wait() {
	<-s.done
}
