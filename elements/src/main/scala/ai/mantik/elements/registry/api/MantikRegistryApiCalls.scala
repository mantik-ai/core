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
package ai.mantik.elements.registry.api

import ai.mantik.elements.{MantikId, NamedMantikId}
import net.reactivecore.fhttp.{ApiBuilder, Output}

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
    { x: String => MantikId.decodeString(x).left.map(_.getMessage) },
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
      .expecting(
        input.Multipart.make(
          input.Multipart.MultipartText("itemId"),
          input.Multipart.MultipartFile("file", fileName = Some("file"))
        )
      )
      .responding(withErrorType(output.circe[ApiFileUploadResponse]()))
  }
}
