package server

import (
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/adapt"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/element/builder"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/images"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
	"log"
	"mime/multipart"
	"net/http"
)

type InputParser = func(r *http.Request) ([]element.Element, error)

// Generate a decoder for Http Requests containing serialized data.
// The generated method has a lot of futures
// - Automatic accepting a variety of Input types
// - Automatic Image conversion
// - Automatic type mapping
// - Handling multipart
func GenerateInputParser(expectedInput ds.DataType) (InputParser, error) {
	deserializer, err := natural.LookupRootElementDeserializer(expectedInput)
	if err != nil {
		return nil, err
	}

	expectingImage := isImage(expectedInput)
	if expectingImage != nil {
		log.Printf("Handler is expecting image %d, %d, extension activated", expectingImage.Width, expectingImage.Height)
	}

	result := func(r *http.Request) ([]element.Element, error) {
		if r.Method != http.MethodPost {
			return nil, errors.New("Only POST supported")
		}
		var elements []element.Element
		var err error
		log.Printf("Content Type %s", r.Header.Get(HeaderContentType))
		err = r.ParseMultipartForm(int64(32 << 20))
		multipartForm := r.MultipartForm
		if err == nil && multipartForm != nil {
			log.Print("Got multipart input")
			if expectingImage != nil {
				elements, err = DecodeImage(expectingImage, multipartForm)
			} else {
				err = errors.New("Got unexpected multipart form")
			}
		} else {
			elements, err = DecodeInput(expectedInput, deserializer, r)
			if err != nil {
				log.Printf("Error decoding input elements %s", err.Error())
				err := errors.Errorf("Error decoding input elements %s", err.Error())
				return nil, err
			}
		}
		return elements, nil
	}

	return result, nil
}

type OutputEncoder = func(resultElements []element.Element, w http.ResponseWriter, r *http.Request)

func GenerateOutputEncoder(expectedOutput ds.DataType) (OutputEncoder, error) {
	serializer, err := natural.LookupRootElementSerializer(expectedOutput)
	if err != nil {
		return nil, err
	}

	result := func(resultElements []element.Element, w http.ResponseWriter, r *http.Request) {
		contentType := figureOutContentType(r)
		err = EncodeOutput(expectedOutput, serializer, resultElements, contentType, w)
		if err != nil {
			log.Printf("Error serializing response %s", err.Error())
			sendError(w, 500, "Could not serialize response")
			return
		}
	}
	return result, nil
}

func figureOutContentType(request *http.Request) string {

	sendType := request.Header.Get(HeaderContentType)
	accepts := request.Header[HeaderAccept]

	allTogether := append(accepts, sendType)
	for _, candidate := range allTogether {
		for _, valid := range SupportedDataMimeTypes {
			if candidate == valid {
				return candidate
			}
		}
	}
	// Fallback
	return MimeJson
}

func accepts(header http.Header, toAccept string) bool {
	accepting := header[HeaderAccept]
	if accepting == nil {
		return false
	}
	for _, v := range accepting {
		if v == toAccept {
			return true
		}
	}
	return false
}

/*
Combines InputParser and OutputEncoder into a Http Handler
*/
func GenerateTypedStreamHandler(
	expectedInput ds.DataType,
	expectedOutput ds.DataType,
	handler func([]element.Element) ([]element.Element, error),
) (func(http.ResponseWriter, *http.Request), error) {

	inputParser, err := GenerateInputParser(expectedInput)
	if err != nil {
		return nil, err
	}
	outputEncoder, err := GenerateOutputEncoder(expectedOutput)
	if err != nil {
		return nil, err
	}
	result := func(w http.ResponseWriter, r *http.Request) {
		elements, err := inputParser(r)
		if err != nil {
			sendError(w, 400, "Bad Request")
			return
		}
		applied, err := handler(elements)
		outputEncoder(applied, w, r)
	}
	return result, nil
}

func isImage(input ds.DataType) *ds.Image {
	inputTabular, ok := input.(*ds.TabularData)
	if !ok {
		return nil
	}
	if len(inputTabular.Columns) != 1 {
		return nil
	}
	inputImage, ok := inputTabular.Columns[0].SubType.Underlying.(*ds.Image)
	if !ok {
		return nil
	}
	return inputImage
}

/* Consumes and decodes input from an http.Request. */
func DecodeInput(expectedType ds.DataType, rootDeserializer natural.ElementDeserializer, r *http.Request) ([]element.Element, error) {
	contentType := r.Header.Get(HeaderContentType)

	// Bundle data
	if contentType == MimeMantikBundle {
		return decodeInputBundle(expectedType, r, serializer.BACKEND_MSGPACK)
	}
	if contentType == MimeMantikBundleJson {
		return decodeInputBundle(expectedType, r, serializer.BACKEND_JSON)
	}

	// Data without header
	var backendType serializer.BackendType
	if contentType == MimeMsgPack {
		backendType = serializer.BACKEND_MSGPACK
	} else {
		backendType = serializer.BACKEND_JSON
	}
	backend, err := serializer.CreateDeserializingBackend(backendType, r.Body)
	if err != nil {
		return nil, err
	}

	var buf []element.Element = nil

	for {
		elem, err := rootDeserializer.Read(backend)
		if err == io.EOF {
			return buf, nil
		}
		if err != nil {
			return nil, err
		}
		buf = append(buf, elem)
	}
}

func decodeInputBundle(expectedType ds.DataType, r *http.Request, backendType serializer.BackendType) ([]element.Element, error) {
	// Bundle Data
	bundle, err := natural.DecodeBundleFromReader(backendType, r.Body)
	if err != nil {
		return nil, err
	}
	adapter, err := adapt.LookupAutoAdapter(bundle.Type, expectedType)
	if err != nil {
		return nil, err
	}
	resultRows := make([]element.Element, len(bundle.Rows))
	for i, r := range bundle.Rows {
		resultRows[i], err = adapter(r)
		if err != nil {
			return nil, err
		}
	}
	return resultRows, nil
}

func DecodeImage(image *ds.Image, form *multipart.Form) ([]element.Element, error) {
	files := form.File
	if len(files) == 0 {
		return nil, errors.New("Can only decode one image")
	}
	var buf []element.Element = nil
	for _, subFiles := range files {
		for _, f := range subFiles {
			println(f.Filename)
			decodedImage, err := decodeSingleImage(image, f)
			if err != nil {
				return nil, err
			}
			buf = append(buf, builder.Row(decodedImage))
		}
	}
	return buf, nil
}

func decodeSingleImage(image *ds.Image, f *multipart.FileHeader) (*element.ImageElement, error) {
	file, err := f.Open()
	if err != nil {
		return nil, err
	}
	defer file.Close()
	return images.AdhocDecode(image, file)
}

/** Writes serialized elements back into the output. */
func EncodeOutput(dataType ds.DataType, rootSerializer natural.ElementSerializer, elements []element.Element, contentType string, w http.ResponseWriter) error {
	var backendType serializer.BackendType
	var withHeader = false

	if contentType == MimeMsgPack {
		backendType = serializer.BACKEND_MSGPACK
		withHeader = false
	} else if contentType == MimeMantikBundle {
		backendType = serializer.BACKEND_MSGPACK
		withHeader = true
	} else if contentType == MimeMantikBundleJson {
		backendType = serializer.BACKEND_JSON
		withHeader = true
	} else {
		// Send JSON if nothing is requested and no MsgPack send
		backendType = serializer.BACKEND_JSON
		withHeader = false
	}

	w.Header().Set(HeaderContentType, contentType)

	backend, err := serializer.CreateSerializingBackend(backendType, w)
	if err != nil {
		return err
	}
	if withHeader {
		err := natural.WriteHeader(backend, element.Header{ds.Ref(dataType)})
		if err != nil {
			return err
		}
	}
	for _, e := range elements {
		err := rootSerializer.Write(backend, e)
		if err != nil {
			return err
		}
	}
	return nil
}
