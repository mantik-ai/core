package server

import (
	"gl.ambrosys.de/mantik/go_shared/serving"
	"net/http"
)

type DataSetServer struct {
	*Server
	dataset serving.ExecutableDataSet
	encoder OutputEncoder
}

func CreateDataSetServer(dataset serving.ExecutableDataSet, address string) (*DataSetServer, error) {
	encoder, err := GenerateOutputEncoder(dataset.Type().Underlying)
	if err != nil {
		return nil, err
	}

	mainServer, err := CreateServer(address)
	s := DataSetServer{
		Server:  mainServer,
		dataset: dataset,
	}

	s.encoder = encoder

	s.serveMux.HandleFunc("/type", CreateFixedJsonHandler(s.dataset.Type()))
	s.serveMux.HandleFunc("/get", s.GetElements)
	return &s, err
}

func (d *DataSetServer) GetElements(w http.ResponseWriter, r *http.Request) {
	d.encoder(d.dataset.Get(), w, r)
}
