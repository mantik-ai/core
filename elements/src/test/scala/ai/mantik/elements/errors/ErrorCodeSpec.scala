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
package ai.mantik.elements.errors

import ai.mantik.testutils.TestBase

class ErrorCodeSpec extends TestBase {

  "isA" should "work" in {
    ErrorCodes.MantikItemNotFound.codePath.startsWith(ErrorCodes.RootCode.codePath) shouldBe true
    ErrorCodes.MantikItemNotFound.isA(ErrorCodes.RootCode) shouldBe true
    ErrorCodes.MantikItem.isA(ErrorCodes.MantikItemNotFound) shouldBe false
    ErrorCodes.MantikItemNotFound.isA(ErrorCodes.MantikItem) shouldBe true
    ErrorCodes.MantikItemNotFound.isA(ErrorCodes.MantikItemNotFound) shouldBe true
    ErrorCodes.MantikItemNotFound.isA(ErrorCodes.MantikItemPayloadNotFound) shouldBe false
  }
}
