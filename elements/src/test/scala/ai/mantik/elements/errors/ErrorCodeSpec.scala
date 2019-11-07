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
