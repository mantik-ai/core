package server

import (
	"encoding/json"
	"fmt"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"net"
	"net/http"
	"time"
)

const HeaderContentType = "Content-Type"
const HeaderAccept = "Accept"
const MimeJson string = natural.MimeJson
const MimeText string = "application/text"
const MimeZip string = "application/zip"
const MimeMsgPack string = natural.MimeMsgPack
const MimeMantikBundle string = natural.MimeMantikBundle
const MimeMantikBundleJson string = natural.MimeMantikBundleJson

var SupportedDataMimeTypes []string = []string{MimeJson, MimeMantikBundleJson, MimeMsgPack, MimeMantikBundle}

// Base for all Bridge servers
type Server struct {
	serveMux   *http.ServeMux
	httpServer *http.Server
	ListenPort int
	listener   net.Listener
}

// Problem: on a random port (unit tests) we cannot easily figure out the port
// when we use httpServer.ListenAndServe
// see https://stackoverflow.com/questions/43424787/how-to-use-next-available-port-in-http-listenandserve
// So we do listen and serve by ourself.

func (s *Server) Listen() error {
	listener, err := net.Listen("tcp", s.httpServer.Addr)
	if err != nil {
		return err
	}
	s.listener = listener
	s.ListenPort = listener.Addr().(*net.TCPAddr).Port
	return nil
}

func (s *Server) Serve() error {
	err := s.httpServer.Serve(s.listener)
	if err == http.ErrServerClosed {
		// this is not an error
		return nil
	}
	return err
}

func (s *Server) ListenAndServe() error {
	if err := s.Listen(); err != nil {
		return err
	}
	return s.Serve()
}

func (s *Server) Address() string {
	return s.httpServer.Addr
}

func (s *Server) Close() {
	s.httpServer.Close()
}

func (s *Server) indexHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set(HeaderContentType, MimeText)
	if r.URL.Path == "/" {
		w.WriteHeader(200)
		w.Write([]byte("This is a Mantik bridge\n"))
	} else {
		sendError(w, http.StatusNotFound, "Page %s not found", r.URL.Path)
	}
}

func (s *Server) quitHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		w.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	w.WriteHeader(200)
	time.AfterFunc(100*time.Millisecond, func() {
		s.Close()
	})
}

/* Send a HTTP error mesage. Returns the code again. */
func sendError(w http.ResponseWriter, code int, format string, a ...interface{}) int {
	// Different error formats, than just text?
	w.WriteHeader(code)
	fmt.Fprintf(w, format, a...)
	w.Write([]byte("\n")) // love your terminal curl users.
	return code
}

// Creates the server
func CreateServer(address string) (*Server, error) {
	var server Server
	server.serveMux = http.NewServeMux()
	server.serveMux.HandleFunc("/", server.indexHandler)
	server.serveMux.HandleFunc("/admin/quit", server.quitHandler)

	server.httpServer = &http.Server{Addr: address, Handler: server.serveMux}
	return &server, nil
}

// Auto detect executable type and returns the server
func CreateServerForExecutable(executable serving.Executable, address string) (*Server, error) {
	a, ok := executable.(serving.ExecutableAlgorithm)
	if ok {
		s, err := CreateAlgorithmServer(a, address)
		if err != nil {
			return nil, err
		}
		return s.Server, nil
	}
	t, ok := executable.(serving.TrainableAlgorithm)
	if ok {
		s, err := CreateTrainableServer(t, address)
		if err != nil {
			return nil, err
		}
		return s.Server, nil
	}
	d, ok := executable.(serving.ExecutableDataSet)
	if ok {
		s, err := CreateDataSetServer(d, address)
		if err != nil {
			return nil, err
		}
		return s.Server, nil
	}
	panic("Unsupported Executable type")
}

func CreateFixedJsonHandler(result interface{}) func(w http.ResponseWriter, r *http.Request) {
	data, err := json.Marshal(result)
	if err != nil {
		panic(err.Error())
	}
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set(HeaderContentType, MimeJson)
		w.WriteHeader(200)
		w.Write(data)
	}
}
