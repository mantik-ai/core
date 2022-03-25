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
package construct

import (
	"bytes"
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/element"
	"github.com/stretchr/testify/assert"
	"io"
	"testing"
)

func TestBinIo(t *testing.T) {
	for _, sample := range element.FundamentalTypeSamples() {
		if sample.Type != ds.Void && sample.Type != ds.String && sample.Type != ds.Bool {
			encoder, err := lookupBinaryWriter(sample.Type.(*ds.FundamentalType))
			assert.NoError(t, err)
			buf := &bytes.Buffer{}
			err = encoder(sample.GetSinglePrimitive(), buf)
			assert.NoError(t, err)
			err = encoder(sample.GetSinglePrimitive(), buf)
			assert.NoError(t, err)

			// and now back
			decoder, err := lookupBinaryReader(sample.Type.(*ds.FundamentalType))
			assert.NoError(t, err)
			reader := bytes.NewReader(buf.Bytes())
			value1, err := decoder(reader)
			assert.NoError(t, err)
			assert.Equal(t, sample.GetSinglePrimitive(), value1)
			value2, err := decoder(reader)
			assert.NoError(t, err)
			assert.Equal(t, sample.GetSinglePrimitive(), value2)
			value3, err := decoder(reader)
			assert.Equal(t, io.EOF, err)
			assert.Nil(t, value3)
		}
	}
}
