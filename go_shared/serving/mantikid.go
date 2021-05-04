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

import "strings"

const DefaultAccount = "library"
const DefaultVersion = "latest"

func FormatNamedMantikId(name string, account *string, version *string) string {
	var accountToUse = DefaultAccount
	if account != nil {
		accountToUse = *account
	}
	var versionToUse = DefaultVersion
	if version != nil {
		versionToUse = *version
	}
	builder := strings.Builder{}
	if accountToUse != DefaultAccount {
		builder.WriteString(accountToUse)
		builder.WriteByte('/')
	}
	builder.WriteString(name)
	if versionToUse != DefaultVersion {
		builder.WriteByte(':')
		builder.WriteString(versionToUse)
	}
	return builder.String()
}
