/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package actions

import (
	"cli/client"
	"cli/protos/mantik/engine"
	"context"
	"fmt"
	"github.com/mantik-ai/core/go_shared/serving"
	"github.com/mantik-ai/core/go_shared/serving/server"
	"github.com/mantik-ai/core/go_shared/util/dirzip"
	"github.com/mantik-ai/core/go_shared/util/osext"
	"github.com/pkg/errors"
	"io"
	"io/ioutil"
	"os"
	"path"
)

type ExtractArguments struct {
	// required
	MantikId string
	// required
	Directory string
}

func ExtractItem(engineClient *client.EngineClient, debug bool, args *ExtractArguments) error {
	if osext.FileExists(args.Directory) {
		return errors.New("Target directory already exists")
	}
	err := os.MkdirAll(args.Directory, os.ModePerm)
	if err != nil {
		return errors.Wrap(err, "Ensuring Target directory failed")
	}
	req := engine.GetArtifactRequest{
		MantikId: args.MantikId,
	}

	requestClient, err := engineClient.LocalRegistry.GetArtifactWithPayload(context.Background(), &req)
	if err != nil {
		return errors.Wrap(err, "Could not get item")
	}
	header, err := requestClient.Recv()
	if err != nil {
		return errors.Wrap(err, "Response Header")
	}

	if debug {
		fmt.Printf("Receiving item...\n")
	}

	mantikHeaderPath := path.Join(args.Directory, serving.MantikHeaderName)
	err = ioutil.WriteFile(mantikHeaderPath, []byte(header.Artifact.MantikHeader), os.ModePerm)
	if err != nil {
		return errors.Wrap(err, "Writing MantikHeader")
	}
	if len(header.ContentType) > 0 {
		if debug {
			fmt.Printf("Downloading payload...\n")
		}
		err := downloadPayload(args.Directory, debug, header, requestClient)
		if err != nil {
			return errors.Wrap(err, "Downloading payload")
		}
	} else {
		if debug {
			fmt.Printf("No Payload\n")
		}
	}

	fmt.Printf("Received Item\n")
	PrintItem(header.Artifact, false, false)

	return nil
}

func downloadPayload(
	dir string,
	debug bool,
	header *engine.GetArtifactWithPayloadResponse,
	requestClient engine.LocalRegistryService_GetArtifactWithPayloadClient,
) error {
	tempFile, err := ioutil.TempFile("", "mantik_download_temp")
	if err != nil {
		return err
	}
	if debug {
		fmt.Printf("Using temporary file %s\n", tempFile.Name())
	}
	defer os.Remove(tempFile.Name())
	bytes := len(header.Payload)
	chunks := 1
	if len(header.Payload) > 0 {
		_, err = tempFile.Write(header.Payload)
		if err != nil {
			return err
		}
	}
	for {
		next, err := requestClient.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			return err
		}
		bytes += len(next.Payload)
		chunks++
		_, err = tempFile.Write(next.Payload)
		if err != nil {
			return err
		}
	}
	if debug {
		fmt.Printf("Downloaded %d bytes in %d chunks\n", bytes, chunks)
	}
	return extractPayload(dir, debug, tempFile.Name(), header.ContentType)
}

func extractPayload(
	dir string,
	debug bool,
	tempFile string,
	contentType string,
) error {
	payloadFile := path.Join(dir, serving.PayloadPathElement)
	switch contentType {
	case server.MimeZip:
		err := os.Mkdir(payloadFile, os.ModePerm)
		if err != nil {
			return err
		}
		return dirzip.UnzipDiectory(tempFile, payloadFile, debug)
	case server.MimeMantikBundle:
		// the temp file should not be deleted then
		err := os.Rename(tempFile, payloadFile)
		if err != nil {
			return err
		}
	default:
		fmt.Printf("Warning, unknown content type %s assuming file type\n", contentType)
		err := os.Rename(tempFile, payloadFile)
		if err != nil {
			return err
		}
	}
	return nil
}
