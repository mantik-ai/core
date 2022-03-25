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
package adapt_test

import (
	"github.com/mantik-ai/core/go_shared/ds"
	"github.com/mantik-ai/core/go_shared/ds/adapt"
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestIdentityConversion(t *testing.T) {
	converter, err := adapt.LookupRawAdapter(ds.Int32, ds.Int32)
	assert.NoError(t, err)
	assert.Equal(t, int32(4), converter(int32(4)))
}

func TestSimpleConversions(t *testing.T) {
	converter, err := adapt.LookupRawAdapter(ds.Uint8, ds.Int64)
	assert.NoError(t, err)
	assert.Equal(t, int64(254), converter(uint8(254)))
}

func TestFailing(t *testing.T) {
	_, err := adapt.LookupRawAdapter(ds.Uint32, ds.Int8)
	assert.Error(t, err)
	_, err = adapt.LookupRawAdapter(ds.String, ds.Int32)
	assert.Error(t, err)
}
