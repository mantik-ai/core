package streamer

import (
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"io"
	"net"
	"net/http"
	"strings"
	"time"
)

/* Notification Callback. */
type StreamNotifyFn = func(reqId int, in int64, out int64, inFailure error, done bool)

/* Notification Delay for Streams. */
const StreamNotificationDelay = time.Second

/* Header name for Content Type. */
const HttpContentType = "Content-Type"
const HttpAccept = "Accept"

const HttpUrlPrefix = "http://"
const TcpUrlPrefix = "tcp://"

// Error returned by if it should be tried again
var tryAgainError = errors.New("Try again")

/**
Open a TCP or HTTP Url.
url .. tcp or http url
retryInterval .. in case of http, the time to wait between 409 responses
contentType .. in case of http, the requested content type (accept-header, optional).
*/
func OpenStream(url string, retryInterval time.Duration, contentType *string) (io.ReadCloser, error) {
	if strings.HasPrefix(url, TcpUrlPrefix) {
		address := strings.TrimPrefix(url, TcpUrlPrefix)
		return net.Dial("tcp", address)
	}
	if strings.HasPrefix(url, HttpUrlPrefix) {
		return OpenHttpStream(url, retryInterval, contentType)
	}
	return nil, errors.Errorf("Unknown protocol in %s", url)
}

func OpenHttpStream(url string, retryInterval time.Duration, contentType *string) (io.ReadCloser, error) {
	var result io.ReadCloser
	var err error
	for {
		result, err = tryHttpGet(url, contentType)
		if err == tryAgainError {
			time.Sleep(retryInterval)
			continue
		} else {
			break
		}
	}

	if err != nil {
		return nil, err
	}
	return result, nil
}

func tryHttpGet(url string, contentType *string) (io.ReadCloser, error) {
	req, err := http.NewRequest(http.MethodGet, url, nil)
	if contentType != nil {
		req.Header.Add(HttpAccept, *contentType)
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
		log.Warnf("Got %d Response on HTTP GET %s", resp.StatusCode, url)
		err = errors.Errorf("Got %d response on HTTP GET", resp.StatusCode)
		return nil, err
	}
	if resp.StatusCode == 204 {
		log.Warn("Empty 204 Response on HTTP GET")
	}
	return resp.Body, nil
}
