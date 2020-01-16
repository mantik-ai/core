package ai.mantik.executor.common

import java.nio.charset.StandardCharsets
import java.util.Base64

import ai.mantik.executor.model.DataProvider

/** Common Payload provider code */
object PayloadProvider {

  /** Create the extra argument list for the payload provider. */
  def createExtraArguments(
    dataProvider: DataProvider
  ): List[String] = {
    val mantikHeaderArgument = dataProvider.mantikHeader.map { mantikHeader =>
      // The container expects the MantikHeader as base64 argument
      val base64Encoder = Base64.getEncoder
      val encodedMantikHeader = base64Encoder.encodeToString(
        mantikHeader.getBytes(StandardCharsets.UTF_8)
      )
      List("-mantikHeader", encodedMantikHeader)
    }.getOrElse(Nil)

    val urlArgument = dataProvider.url.map { url =>
      List("-url", url)
    }.getOrElse(Nil)

    mantikHeaderArgument ++ urlArgument
  }
}
