package actions

import (
	"cli/client"
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/pkg/errors"
	"gl.ambrosys.de/mantik/go_shared/serving"
	"gl.ambrosys.de/mantik/go_shared/serving/server"
	"gl.ambrosys.de/mantik/go_shared/util/dirzip"
	"gl.ambrosys.de/mantik/go_shared/util/osext"
	"io"
	"io/ioutil"
	"os"
	"path"
)

/* Arguments for adding directories into Mantik. */
type AddArguments struct {
	// Optional
	NamedMantikId string
	// Directory
	Directory string
}

func AddItem(engineClient *client.EngineClient, debug bool, args *AddArguments) error {
	mantikfilePath := path.Join(args.Directory, serving.MantikfileName)
	mfContent, err := ioutil.ReadFile(mantikfilePath)
	if err != nil {
		return errors.Wrapf(err, "Loading %s", mantikfilePath)
	}
	mf, err := serving.ParseMantikFile(mfContent)
	if err != nil {
		return errors.Wrapf(err, "Parsing %s", mantikfilePath)
	}
	var namedMantikId *string
	if args.NamedMantikId != "" {
		namedMantikId = &args.NamedMantikId
	} else {
		namedMantikId = mf.Header().NamedMantikId()
	}

	requestClient, err := engineClient.LocalRegistry.AddArtifact(context.Background())
	if err != nil {
		return err
	}

	payloadPath := path.Join(args.Directory, serving.PayloadPathElement)
	hasPayload := hasPayload(payloadPath)
	var contentType string
	if hasPayload {
		contentType, err = payloadContentType(payloadPath)
		fmt.Printf("Payload Content Type: %s\n", contentType)
	} else {
		fmt.Printf("No Payload\n")
	}

	if debug {
		fmt.Printf("Sending Header...\n")
	}

	header := engine.AddArtifactRequest{
		NamedMantikId: client.EncodeOptionalString(namedMantikId),
		Mantikfile:    string(mfContent),
		ContentType:   contentType,
	}
	err = requestClient.Send(&header)
	if err != nil {
		return errors.Wrap(err, "Could not send header")
	}

	payloadBytes := 0
	requestCount := 1
	if hasPayload {
		if debug {
			fmt.Printf("Uploading Payload...\n")
		}
		payloadStream, err := openPayloadStream(payloadPath, contentType, debug)
		if err != nil {
			return errors.Wrap(err, "Could not read payload")
		}
		buf := make([]byte, BufSize, BufSize)
		for {
			n, err := payloadStream.Read(buf)
			if n > 0 {
				subReqest := engine.AddArtifactRequest{
					Payload: buf[:n],
				}
				sendErr := requestClient.Send(&subReqest)
				if sendErr != nil {
					return errors.Wrap(sendErr, "Error on payload sending")
				}
				payloadBytes += n
				requestCount += 1
			}
			if err == io.EOF {
				break
			}
			if err != nil {
				return errors.Wrap(err, "Error on reading payload")
			}
		}
	}

	if debug {
		fmt.Printf("Sending Request Done (payload=%d bytes, requests=%d)...\n", payloadBytes, requestCount)
	}

	response, err := requestClient.CloseAndRecv()
	if err != nil {
		return errors.Wrap(err, "Got error on adding")
	}
	fmt.Printf("Adding Item Successfull\n")
	PrintItem(response.Artifact, false, false)
	return nil
}

const BufSize = 100 * 1024

// Figure out if the payload path exists
func hasPayload(payloadPath string) bool {
	return osext.FileExists(payloadPath)
}

// Figure out the content type of the payload
func payloadContentType(payloadPath string) (string, error) {
	if !osext.FileExists(payloadPath) {
		return "", errors.New("Payload doesn't exist")
	}
	if osext.IsDirectory(payloadPath) {
		return server.MimeZip, nil
	} else {
		// TODO: More types ?!
		return server.MimeMantikBundle, nil
	}
}

func openPayloadStream(payloadPath string, contentType string, debug bool) (io.ReadCloser, error) {
	if contentType == server.MimeZip {
		pipeReader, pipeWriter := io.Pipe()
		go func() {
			err := dirzip.ZipDirectoryToStream(payloadPath, debug, pipeWriter)
			if err != nil {
				pipeWriter.CloseWithError(err)
			} else {
				pipeWriter.Close()
			}
		}()
		return pipeReader, nil
	} else {
		return os.Open(payloadPath)
	}
}
