package ai.mantik.elements.registry.api

import ai.mantik.elements.MantikId
import net.reactivecore.fhttp.{ ApiBuilder, Output }

import scala.util.Try

object MantikRegistryApiCalls extends ApiBuilder {
  val TokenHeaderName = "AUTH_TOKEN"

  private def withErrorType[T <: Output](success: T) = Output.ErrorSuccess(
    output.circe[ApiErrorResponse](),
    success
  )

  /** Execute a login call. */
  val login = add {
    post("login")
      .expecting(input.circe[ApiLoginRequest]())
      .responding(withErrorType(output.circe[ApiLoginResponse]()))
  }

  /** Execute a Login Status call. */
  val loginStatus = add {
    get("login_status")
      .expecting(input.AddHeader(TokenHeaderName))
      .responding(withErrorType(output.circe[ApiLoginStatusResponse]()))
  }

  val MantikIdMapping = input.pureMapping[String, MantikId](
    { x: String => Try(MantikId.fromString(x)).toEither.left.map(_.toString) },
    { y: MantikId => Right(y.toString) }
  )

  /** Retrieve an artifact. */
  val artifact = add {
    get("artifact")
      .expecting(input.AddHeader(TokenHeaderName))
      .expecting(input.MappedInput(input.AddQueryParameter("mantikId"), MantikIdMapping))
      .responding(withErrorType(output.circe[ApiGetArtifactResponse]()))
  }

  /** Tag an Artifact. */
  val tag = add {
    post("artifact", "tag")
      .expecting(input.AddHeader(TokenHeaderName))
      .expecting(input.circe[ApiTagRequest]())
      .responding(withErrorType(output.circe[ApiTagResponse]()))
  }

  /**
   * Retrieve an artifact file.
   *
   * @return content type and byte source.
   */
  val file = add {
    get("artifact", "file")
      .expecting(input.AddHeader(TokenHeaderName))
      .expecting(input.AddQueryParameter("fileId"))
      .responding(withErrorType(output.Binary))
  }

  /** Prepares the upload of a file. */
  val prepareUpload = add {
    post("artifact")
      .expecting(input.AddHeader(TokenHeaderName))
      .expecting(input.circe[ApiPrepareUploadRequest]())
      .responding(withErrorType(output.circe[ApiPrepareUploadResponse]()))
  }

  /** Uploades the payload of an artifact. */
  val uploadFile = add {
    post("artifact", "file")
      .expecting(input.AddHeader(TokenHeaderName))
      .expecting(input.Multipart.make(
        input.Multipart.MultipartText("itemId"),
        input.Multipart.MultipartFile("file", fileName = Some("file"))
      ))
      .responding(withErrorType(output.circe[ApiFileUploadResponse]()))
  }
}

