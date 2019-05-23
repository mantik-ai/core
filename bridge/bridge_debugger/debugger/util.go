package debugger

import (
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
	"os"
)

func ExitOnError(err error, op string) {
	if err != nil {
		println("Fatal Error on ", op, err.Error())
		os.Exit(1)
	}
}

func readBundleFromFile(inputFile string) (*element.Bundle, error) {
	file, err := os.Open(inputFile)
	if err != nil {
		return nil, err
	}
	defer file.Close()
	bundle, err := natural.DecodeBundleFromReader(serializer.BACKEND_MSGPACK, file)
	if err != nil {
		return nil, err
	}
	return bundle, err
}

func writeBundleToFile(bundle *element.Bundle, outputFile string) error {
	file, err := os.OpenFile(outputFile, os.O_WRONLY|os.O_CREATE, 0660)
	if err != nil {
		return err
	}
	defer file.Close()
	err = natural.EncodeBundleToWriter(bundle, serializer.BACKEND_MSGPACK, file)
	return err
}

func writeStreamToFile(reader io.ReadCloser, fileName string) error {
	file, err := os.OpenFile(fileName, os.O_WRONLY|os.O_CREATE, 0660)
	if err != nil {
		return err
	}
	defer file.Close()
	defer reader.Close()
	_, err = io.Copy(file, reader)
	return err
}