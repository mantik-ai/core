package testutil

import (
	"bytes"
	"fmt"
	"github.com/pkg/errors"
	log "github.com/sirupsen/logrus"
	"io/ioutil"
	"net/http"
	"net/http/httptest"
)

type SampleServer struct {
	*httptest.Server
	mux            *http.ServeMux
	Requests       int
	RequestData    [][]byte
	MimeTypes      []string
	LastError      error
	resource       string
	QuitRequested  bool
	StatusToReturn int // if not 200, no data will be returned
}

func (s *SampleServer) ResourceUrl() string {
	return s.URL + "/" + s.resource
}

func createSampleServer(resource string) *SampleServer {
	var r SampleServer
	r.mux = http.NewServeMux()
	r.mux.HandleFunc("/admin/quit", func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost {
			writer.WriteHeader(http.StatusMethodNotAllowed)
		} else {
			r.QuitRequested = true
			writer.WriteHeader(200)
		}
	})
	r.mux.HandleFunc("/", func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet {
			writer.WriteHeader(http.StatusMethodNotAllowed)
		} else {
			writer.WriteHeader(r.StatusToReturn)
		}
	})
	r.Server = httptest.NewServer(r.mux)
	r.resource = resource
	r.StatusToReturn = 200
	return &r
}

func CreateSampleSource(resource string, payload []byte) *SampleServer {
	r := createSampleServer(resource)
	r.mux.Handle(ensureBeginningSlash(resource), http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			r.LastError = errors.New("Got wrong method")
			return
		} else {
			r.MimeTypes = append(r.MimeTypes, request.Header.Get("Accept"))
			r.Requests++
			if r.StatusToReturn == 200 {
				writer.Write(payload)
			} else {
				writer.WriteHeader(r.StatusToReturn)
			}
		}
	}))
	return r
}

func CreateSampleSink(resource string) *SampleServer {
	r := createSampleServer(resource)
	r.mux.Handle(ensureBeginningSlash(resource), http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		} else {
			writer.WriteHeader(r.StatusToReturn)
			r.Requests++
			bodyContent, err := ioutil.ReadAll(request.Body)
			r.MimeTypes = append(r.MimeTypes, request.Header.Get("Content-Type"))
			r.RequestData = append(r.RequestData, bodyContent)
			if err != nil {
				r.LastError = err
			}
		}
	}))
	return r
}

// Simple transformation, which just forwards data
func CreateSampleTransformation(resource string) *SampleServer {
	r := createSampleServer(resource)
	r.mux.Handle(ensureBeginningSlash(resource), http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		} else {
			// Note: Tricky
			// We cannot write back the request on-the-fly as golang doesn't support it
			// so we have to cache it
			//
			// By Default Go closes the Body on the first flush of the Response body
			// https://stackoverflow.com/questions/53111112/invalid-read-on-closed-body-on-google-app-engine
			//
			// This makes our design of Transformation Functions more tricky.
			body, err := ioutil.ReadAll(request.Body)
			if err != nil {
				log.Infof("Got error on decoding body %s", err.Error())
				r.LastError = err
			}
			writer.WriteHeader(r.StatusToReturn)
			if r.StatusToReturn == 200 {
				_, err = writer.Write(body)
				if err != nil {
					log.Infof("Got error on encoding response body %s", err.Error())
					r.LastError = err
				}
			}
			r.Requests++
			r.MimeTypes = append(r.MimeTypes, request.Header.Get("Content-Type"))
			r.RequestData = append(r.RequestData, body)
		}
	}))
	return r
}

// A Server with one input POST Url and two outgoing URLs which do not respond, until there is something in input resource
// learnState contains number of bytes as string
// learnResult contains every 2nd byte.
func CreateLearnLikeServer(inputResource string, learnStateResource string, learnResultResource string) *SampleServer {
	r := createSampleServer(inputResource)

	learnState := make(chan []byte, 1)
	learnResult := make(chan []byte, 1)

	r.mux.HandleFunc(ensureBeginningSlash(inputResource), func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		} else {
			writer.WriteHeader(r.StatusToReturn)
			body, err := ioutil.ReadAll(request.Body)
			if err != nil {
				log.Info("Got error on reading learn like server")
				writer.WriteHeader(500)
				return
			}
			learnState <- []byte(fmt.Sprintf("%d", len(body)))
			var buf bytes.Buffer

			for idx, b := range body {
				if idx%2 == 0 {
					buf.WriteByte(b)
				}
			}
			learnResult <- buf.Bytes()
			r.Requests++
		}
	})
	r.mux.HandleFunc(ensureBeginningSlash(learnStateResource), func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		writer.WriteHeader(r.StatusToReturn)
		if r.StatusToReturn == 200 {
			writer.Write(<-learnState)
		}
	})
	r.mux.HandleFunc(ensureBeginningSlash(learnResultResource), func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		writer.WriteHeader(r.StatusToReturn)
		if r.StatusToReturn == 200 {
			writer.Write(<-learnResult)
		}
	})
	return r
}

func ensureBeginningSlash(s string) string {
	if len(s) == 0 || s[0] != '/' {
		return "/" + s
	} else {
		return s
	}
}
