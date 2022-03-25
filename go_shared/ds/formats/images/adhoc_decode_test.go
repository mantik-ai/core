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
package images

import (
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/stretchr/testify/assert"
	"io/ioutil"
	"math"
	"testing"
)

var sampleImage = ds.Image{
	3, 5,
	[]ds.ImageComponentElement{
		{ds.Green, ds.ImageComponent{ds.Ref(ds.Uint8)}},
		{ds.Blue, ds.ImageComponent{ds.Ref(ds.Uint8)}},
		{ds.Green, ds.ImageComponent{ds.Ref(ds.Uint8)}},
	},
	"plain",
}

var grayScaleImage = ds.Image{
	202, 65,
	[]ds.ImageComponentElement{
		{ds.Black, ds.ImageComponent{ds.Ref(ds.Float32)}},
	},
	"plain",
}

func TestAdhocDecodePng(t *testing.T) {
	fileContent, err := ioutil.ReadFile("../../../test/resources/images/reactivecore.png")
	assert.NoError(t, err)
	converted, err := AdhocDecode(&sampleImage, bytes.NewBuffer(fileContent))
	assert.NoError(t, err)
	assert.Equal(t, 3*5*3, len(converted.Bytes))
	var min float64
	var max float64
	for _, b := range converted.Bytes {
		min = math.Min(min, float64(b))
		max = math.Max(max, float64(b))
	}
	assert.True(t, min == 0.0)
	assert.True(t, max > 100)
}

func TestAdhocDecodeJpeg(t *testing.T) {
	fileContent, err := ioutil.ReadFile("../../../test/resources/images/two_2_inverted.jpg")
	assert.NoError(t, err)
	expected := ds.CreateSingleChannelRawImage(
		28, 28, ds.Black, ds.Uint8,
	)
	converted, err := AdhocDecode(expected, bytes.NewBuffer(fileContent))
	assert.NoError(t, err)
	var min float64
	var max float64
	for _, b := range converted.Bytes {
		min = math.Min(min, float64(b))
		max = math.Max(max, float64(b))
	}
	assert.Equal(t, 784, len(converted.Bytes))
	assert.Equal(t, 0.0, min)
	assert.Equal(t, 255.0, max)

	// Top left is a white (RGB), however the semantic of the format wants 0 here, as black gets positive pixels
	assert.Equal(t, byte(0), converted.Bytes[0])
}

func TestAdhocDecodeGray(t *testing.T) {
	fileContent, err := ioutil.ReadFile("../../../test/resources/images/reactivecore.png")
	assert.NoError(t, err)
	converted, err := AdhocDecode(&grayScaleImage, bytes.NewBuffer(fileContent))
	assert.NoError(t, err)
	assert.Equal(t, 202*65*4, len(converted.Bytes))
}
