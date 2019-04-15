package streamer

import (
	"coordinator/service/progressutil"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"net"
	"net/http"
	"time"
)

/* Stream an HTTP Resource as a TCP Port. */
type HttpGetSource struct {
	Port          int
	Url           string
	listener      net.Listener
	notifyFn      StreamNotifyFn
	nextRequestId int
	contentType   *string
	retryInterval time.Duration
}

/* Fetch data from HTTP Get and present it on a TCP Socket on the fly. */
func CreateHttpGetSource(url string, contentType *string, notifyFn StreamNotifyFn, retryInterval time.Duration) (*HttpGetSource, error) {
	listener, err := net.Listen("tcp", ":0")
	if err != nil {
		return nil, err
	}
	tcpAddr := listener.Addr().(*net.TCPAddr)
	var r HttpGetSource
	r.Port = tcpAddr.Port
	r.Url = url
	r.notifyFn = notifyFn
	r.listener = listener
	r.contentType = contentType
	r.retryInterval = retryInterval
	log.Debugf("Created http get source from %s at port %d", url, r.Port)
	go r.accept()
	return &r, nil
}

func (s *HttpGetSource) accept() {
	for {
		conn, err := s.listener.Accept()
		if err != nil {
			log.Warnf("Error on accepting TCP Connection %s", err.Error())
			return
		}
		requestId := s.nextRequestId
		s.nextRequestId++
		go s.serveConnection(requestId, conn)
	}
}

func (s *HttpGetSource) Close() {
	s.listener.Close()
}

func (s *HttpGetSource) serveConnection(requestId int, c net.Conn) {
	var resp *http.Response
	var err error
	for {
		resp, err = s.tryGetHtpSource()
		if err == tryAgainError {
			time.Sleep(s.retryInterval)
			continue
		} else {
			break
		}
	}
	if err != nil {
		s.notifyFn(requestId, 0, 0, err, false)
		c.Close()
		return
	}

	defer resp.Body.Close()
	defer c.Close()
	progressutil.CopyAndNotify(StreamNotificationDelay, resp.Body, c, func(bytes int64, failed error, done bool) {
		s.notifyFn(requestId, 0, bytes, failed, done)
	})
}

// Error returned by tryGetHttpSource, if it should be tried again
var tryAgainError = errors.New("Try again")

func (s *HttpGetSource) tryGetHtpSource() (*http.Response, error) {
	req, err := http.NewRequest(http.MethodGet, s.Url, nil)
	if s.contentType != nil {
		req.Header.Add(HttpAccept, *s.contentType)
	}
	if err != nil {
		log.Warnf("Could not create HTTP request %s", err.Error())
		return nil, err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		log.Warnf("Got an error on HTTP Get %s", err.Error())
		return nil, err
	}
	if resp.StatusCode == 409 {
		log.Info("Got 409 on HTTP Get, will try again later")
		return nil, tryAgainError
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		log.Warnf("Got %d Response on HTTP GET %s", resp.StatusCode, s.Url)
		err = errors.Errorf("Got %d response on HTTP GET", resp.StatusCode)
		return nil, err
	}
	if resp.StatusCode == 204 {
		log.Warn("Empty 204 Response on HTTP GET")
	}
	return resp, nil
}
