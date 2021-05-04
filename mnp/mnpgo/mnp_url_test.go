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
package mnpgo

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestParseMnpUrl(t *testing.T) {
	url := "mnp://myhost.name:4000/session1/1234"
	parsed, err := ParseMnpUrl(url)
	assert.NoError(t, err)
	assert.Equal(t, "myhost.name:4000", parsed.Address)
	assert.Equal(t, "session1", parsed.SessionId)
	assert.Equal(t, 1234, parsed.Port)

	assert.Equal(t, url, parsed.String())
}

func TestParseShortUrl(t *testing.T) {
	url := "mnp://myhost.name:4000/session1"
	parsed, err := ParseMnpUrl(url)
	assert.NoError(t, err)

	assert.Equal(t, "myhost.name:4000", parsed.Address)
	assert.Equal(t, "session1", parsed.SessionId)
	assert.Equal(t, -1, parsed.Port)

	assert.Equal(t, url, parsed.String())
}
