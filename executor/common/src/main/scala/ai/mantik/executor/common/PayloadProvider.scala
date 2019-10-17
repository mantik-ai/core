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
    val mantikfileArgument = dataProvider.mantikfile.map { mantikfile =>
      // The container expects the Mantikfile as base64 argument
      val base64Encoder = Base64.getEncoder
      val encodedMantikfile = base64Encoder.encodeToString(
        mantikfile.getBytes(StandardCharsets.UTF_8)
      )
      List("-mantikfile", encodedMantikfile)
    }.getOrElse(Nil)

    val urlArgument = dataProvider.url.map { url =>
      List("-url", url)
    }.getOrElse(Nil)

    mantikfileArgument ++ urlArgument
  }
}
