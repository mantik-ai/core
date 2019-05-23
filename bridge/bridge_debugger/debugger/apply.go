package debugger

import (
	"bridge_debugger/debugger/req"
	"bytes"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
)

func ApplyBridge(url string, inputFile string, outputFile string) {
	inputBundle, err := readBundleFromFile(inputFile)
	ExitOnError(err, "Reading Input File")

	var expectedType ds.DataType
	algorithmType, err := req.GetAlgorithmType(url, "type")
	if err != nil {
		println("Could not get algorithm type ", err.Error())
		println("Assuming type is ok")
		expectedType = inputBundle.Type
	} else {
		expectedType = algorithmType.Input.Underlying
	}

	// Note: this is not really efficient, as converting and posting can also be done on the fly
	// without buffering results.

	println("Bundle Type:   ", ds.ToJsonString(inputBundle.Type))
	println("Expected Type: ", ds.ToJsonString(expectedType))
	adaptedBundle, err := adaptBundle(inputBundle, expectedType)
	ExitOnError(err, "Adapting input type")

	encoded, err := natural.EncodeBundle(adaptedBundle, serializer.BACKEND_MSGPACK)
	ExitOnError(err, "Encoding Bundle")

	response, err := req.PostMantikBundle(url, "apply", bytes.NewReader(encoded))
	ExitOnError(err, "Could not create request")
	err = writeStreamToFile(response, outputFile)
	ExitOnError(err, "Could not write response")
}