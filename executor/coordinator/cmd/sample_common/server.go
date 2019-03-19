package sample_common

import (
	"context"
	"flag"
	"fmt"
	log "github.com/sirupsen/logrus"
	"net"
	"net/http"
	"os"
)

// Common server code for the example servers
type Server struct {
	http.Server
	Mux      *http.ServeMux
	Name     string
	listener *net.TCPListener
}

// Create the Server from command line arguments
func CreateServer(name string) (*Server, error) {
	options := flag.NewFlagSet(name, flag.ExitOnError)
	port := options.Int("port", 8502, "Port")
	err := options.Parse(os.Args[1:])
	if err != nil {
		return nil, err
	}
	address := fmt.Sprintf(":%d", *port)
	return CreateServerWithAddress(name, address)
}

// Like CreateServer, but exit the application if it fails.
func CrateServerWithForce(name string) *Server {
	s, err := CreateServer(name)
	if err != nil {
		println("Server creation failed", err.Error())
		os.Exit(1)
	}
	return s
}

func CreateServerWithAddress(name string, address string) (*Server, error) {
	var s Server
	s.Mux = http.NewServeMux()
	s.Handler = s.Mux
	s.addDefaultHandlers()
	s.Name = name

	listener, err := net.Listen("tcp", address)
	if err != nil {
		return nil, err
	}
	s.listener = listener.(*net.TCPListener)
	return &s, nil
}

func (s *Server) addDefaultHandlers() {
	s.AddPost("Quit the Server", "/admin/quit", s.adminQuit)
	s.AddGet("Hello World Page", "/", func(writer http.ResponseWriter, request *http.Request) {
		writer.Write([]byte(s.Name))
	})
}

func (s *Server) Run() error {
	err := s.Serve(s.listener)
	if err == http.ErrServerClosed {
		return nil
	}
	return err
}

// Like Run() but  exits the application if it fails.
func (s *Server) RunWithForce() {
	err := s.Run()
	if err != nil {
		println("Running failed", err.Error())
		os.Exit(2)
	}
}

func (s *Server) adminQuit(writer http.ResponseWriter, request *http.Request) {
	if request.Method != http.MethodPost {
		writer.WriteHeader(http.StatusMethodNotAllowed)
		return
	}
	log.Infof("Got graceful quit request")
	go func() {
		s.Server.Shutdown(context.Background())
	}()
	writer.WriteHeader(200)
}

// Convenience call to add a GET Call.
func (s *Server) AddGet(description string, pattern string, handlerFunc http.HandlerFunc) {
	log.Infof("Adding GET %s  (%s)", pattern, description)
	s.Mux.HandleFunc(pattern, func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodGet {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		handlerFunc(writer, request)
	})
}

// Convenience call to add a POST Call.
func (s *Server) AddPost(description string, pattern string, handlerFunc http.HandlerFunc) {
	log.Infof("Adding POST %s  (%s)", pattern, description)
	s.Mux.HandleFunc(pattern, func(writer http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost {
			writer.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		handlerFunc(writer, request)
	})
}
