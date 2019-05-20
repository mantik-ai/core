package debugger

import (
	"bridge_debugger/debugger/req"
	"bytes"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"os"
)

func TrainBridge(url string, input string, statsOut string, trainResultOut string) {
	println("Starting Training of ", url, " input: ", input, " statsOut", statsOut, "train result", trainResultOut)

	bundle, err := readBundleFromFile(input)
	ExitOnError(err, "Loading Input Data")

	expectedType, err := req.GetDataType(url, "/training_type")
	if err != nil {
		println("Could not get training type ", err.Error())
		println("Assuming type is ok")
		expectedType = bundle.Type
	}
	println("Bundle Type:   ", ds.ToJsonString(bundle.Type))
	println("Expected Type: ", ds.ToJsonString(expectedType))
	adaptedBundle, err := adaptBundle(bundle, expectedType)
	ExitOnError(err, "Adapting Input Data")

	serialized, err := natural.EncodeBundle(adaptedBundle, serializer.BACKEND_MSGPACK)
	ExitOnError(err, "Serializing Bundle")

	response, err := req.PostResource(url, "train", server.MimeMantikBundle, bytes.NewReader(serialized))
	ExitOnError(err, "Could not issue train request")

	if response.StatusCode != 200 {
		println("Received status ", response.StatusCode)
		os.Exit(1)
	}

	stats, err := req.GetMantikBundle(url, "stats")
	ExitOnError(err, "Fetching Stats")
	err = writeStreamToFile(stats, statsOut)
	ExitOnError(err, "Writing Stats")

	trainingResult, err := req.GetZipFile(url, "result")
	ExitOnError(err, "Fetching Train Result")
	err = writeStreamToFile(trainingResult, trainResultOut)
	ExitOnError(err, "Writing Train Result")
}

func adaptBundle(in *element.Bundle, expectedType ds.DataType) (*element.Bundle, error) {
	if ds.DataTypeEquality(in.Type, expectedType) {
		// Nothing to do
		return in, nil
	}
	cast, err := adapt.LookupCast(in.Type, expectedType)
	if err != nil {
		return nil, errors.Wrap(err, "Could not adapt type")
	}

	if cast.Loosing {
		println("Warn: Cast is loosing precision")
	}
	if cast.CanFail {
		println("Warn: Cast can fail")
	}
	newElements, err := adapt.ApplyForMany(cast.Adapter, in.Rows)
	if err != nil {
		return nil, err
	}
	return &element.Bundle{expectedType, newElements}, nil
}
