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
package serving

import (
	"github.com/stretchr/testify/assert"
	"testing"
)

func TestFormatNamedMantikId(t *testing.T) {
	assert.Equal(t, "abc", FormatNamedMantikId("abc", nil, nil))
	assert.Equal(t, "acc/abc:version", FormatNamedMantikId("abc", sptr("acc"), sptr("version")))
	assert.Equal(t, "abc:version", FormatNamedMantikId("abc", nil, sptr("version")))
	assert.Equal(t, "acc/abc", FormatNamedMantikId("abc", sptr("acc"), nil))
	assert.Equal(t, "abc", FormatNamedMantikId("abc", sptr("library"), sptr("latest"))) // defaults
}

// Return a pointer to a string
// (workaround as we can't take it from constants)
func sptr(s string) *string {
	return &s
}
