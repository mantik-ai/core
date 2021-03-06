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
package server

import (
	"fmt"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/formats/natural"
	"github.com/mantik-ai/core/go_shared/ds/util/serializer"
	"github.com/mantik-ai/core/go_shared/serving/test"
	"github.com/stretchr/testify/assert"
	"io/ioutil"
	"net/http"
	"testing"
)

func TestDataSetStandardPages(t *testing.T) {
	dataset := test.NewDataSet()
	defer dataset.Cleanup()

	server, err := CreateServerForExecutable(dataset, ":0")
	assert.NoError(t, err)
	err = server.Listen()

	assert.NoError(t, err)
	url := fmt.Sprintf("http://localhost:%d", server.ListenPort)

	go server.Serve()
	defer server.Close()

	t.Run("index", func(t *testing.T) {
		response, err := http.Get(url + "/")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
	})

	t.Run("type", func(t *testing.T) {
		response, err := http.Get(url + "/type")
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		assert.Equal(t, MimeJson, response.Header.Get(HeaderContentType))
		content, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		dataType := ds.FromJsonStringOrPanicRef(string(content))
		assert.Equal(t, dataset.Type(), dataType)
	})
	t.Run("get (json)", func(t *testing.T) {
		response, err := getWith409Support(url+"/get", MimeJson)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		block, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		str := string(block)
		assert.Equal(t, `[["Hello",1],["World",2]]`, str)
	})

	t.Run("get", func(t *testing.T) {
		response, err := getWith409Support(url+"/get", MimeMantikBundle)
		assert.NoError(t, err)
		assert.Equal(t, http.StatusOK, response.StatusCode)
		block, err := ioutil.ReadAll(response.Body)
		assert.NoError(t, err)
		parsed, err := natural.DecodeBundle(serializer.BACKEND_MSGPACK, block)
		assert.NoError(t, err)
		assert.Equal(t, dataset.Type().Underlying, parsed.Type)
		assert.Equal(t, 2, len(parsed.Rows))
	})
}
