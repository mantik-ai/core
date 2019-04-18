package server

import (
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/util/async"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"log"
	"net/http"
	"time"
)

type TrainableServer struct {
	*Server
	trainable       serving.TrainableAlgorithm
	trainFinished   *async.WaitCon // result of learning
	trainDataParser InputParser
	statsEncoder    OutputEncoder
	statResult      []element.Element
}

func CreateTrainableServer(trainable serving.TrainableAlgorithm, address string) (*TrainableServer, error) {
	mainServer, err := CreateServer(address)
	s := TrainableServer{
		Server:    mainServer,
		trainable: trainable,
	}
	s.trainDataParser, err = GenerateInputParser(trainable.TrainingType().Underlying)
	if err != nil {
		return nil, err
	}
	s.statsEncoder, err = GenerateOutputEncoder(trainable.StatType().Underlying)
	if err != nil {
		return nil, err
	}

	s.trainFinished = async.NewWaitCon()
	s.serveMux.HandleFunc("/train", s.trainHandler)
	s.serveMux.HandleFunc("/type", CreateFixedJsonHandler(trainable.Type()))
	s.serveMux.HandleFunc("/result", s.learnResultHandler)
	s.serveMux.HandleFunc("/stats", s.statsHandler)
	s.serveMux.HandleFunc("/stat_type", CreateFixedJsonHandler(trainable.StatType()))
	s.serveMux.HandleFunc("/training_type", CreateFixedJsonHandler(trainable.TrainingType()))
	return &s, err
}

func (s *TrainableServer) trainHandler(w http.ResponseWriter, r *http.Request) {
	data, err := s.trainDataParser(r)
	if err != nil {
		sendError(w, 400, "Could not parse training data")
		return
	}
	log.Printf("Starting training with %d rows", len(data))
	statResult, err := s.trainable.Train(data)
	if err != nil {
		log.Printf("Training failed %s", err.Error())
		sendError(w, 500, "Training failed")
		return
	}
	log.Print("Training succeeded")
	s.statResult = statResult
	s.trainFinished.Finish(err)
	w.WriteHeader(200)
}

func (s *TrainableServer) learnResultHandler(w http.ResponseWriter, r *http.Request) {
	err := s.trainFinished.WaitFor(time.Second)
	if err == async.TimeoutError {
		// See spec
		w.WriteHeader(409)
		return
	}

	if err != nil {
		sendError(w, http.StatusInternalServerError, "Learning failed %s", err.Error())
		return
	}

	resultDir, err := s.trainable.LearnResultDirectory()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Could not get learn result directory %s", err.Error())
		return
	}
	w.Header().Set(HeaderContentType, MimeZip)
	err = dirzip.ZipDirectoryToStream(resultDir, false, w)
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Could not write learn result directory %s", err.Error())
		return
	}
}

func (s *TrainableServer) statsHandler(w http.ResponseWriter, r *http.Request) {
	err := s.trainFinished.WaitFor(time.Second)
	if err == async.TimeoutError {
		// See spec
		w.WriteHeader(409)
		return
	}
	s.statsEncoder(s.statResult, w, r)
}
