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
package mnp_pipeline

import (
	"bytes"
	"fmt"
	"github.com/stretchr/testify/assert"
	"gl.ambrosys.de/mantik/go_shared/ds"
	"gl.ambrosys.de/mantik/go_shared/ds/element"
	"gl.ambrosys.de/mantik/go_shared/ds/formats/natural"
	"gl.ambrosys.de/mantik/go_shared/ds/util/serializer"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"testing"
)

// Test accepting of images (regression of #99)
func TestAcceptImage(t *testing.T) {

	inputType := ds.BuildTabular().Add("x", ds.CreateSingleChannelRawImage(
		28, 28, ds.Black, ds.Uint8,
	)).Result()
	pipe := MnpPipeline{
		nil, // no steps, it's just about accepting
		ds.Ref(inputType),
		"pipe",
	}
	server, err := CreateServer(&pipe, 0)
	assert.NoError(t, err)

	err = server.Listen()
	assert.NoError(t, err)

	go func() {
		server.Serve()
	}()
	defer server.Close()

	url := fmt.Sprintf("http://localhost:%d/apply", server.ListenPort)
	testFile := "../../../../go_shared/test/resources/images/two_2_inverted.jpg"
	req, err := MakePostFileRequest(url, testFile)
	assert.NoError(t, err)
	res, err := http.DefaultClient.Do(req)
	assert.NoError(t, err)
	assert.Equal(t, 200, res.StatusCode)
	// response should be the same image in JSON notation [[1,2,3...]].

	parsed, err := natural.DecodeBundleValue(inputType, serializer.BACKEND_JSON, res.Body)
	assert.NoError(t, err)
	assert.Equal(t, 1, len(parsed.Rows))
	assert.Equal(t, 28*28, len(parsed.Rows[0].(*element.TabularRow).Columns[0].(*element.ImageElement).Bytes))
}

func MakePostFileRequest(url string, filename string) (*http.Request, error) {
	file, err := os.Open(filename)
	if err != nil {
		return nil, err
	}
	var b bytes.Buffer
	w := multipart.NewWriter(&b)
	fileWriter, err := w.CreateFormFile("image", "file1")
	_, err = io.Copy(fileWriter, file)
	w.Close()
	req, err := http.NewRequest(http.MethodPost, url, &b)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", w.FormDataContentType())
	return req, err
}
