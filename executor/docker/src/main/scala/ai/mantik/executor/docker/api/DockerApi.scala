package ai.mantik.executor.docker.api

import ai.mantik.executor.docker.api.structures.{ ContainerWaitResponse, CreateContainerRequest, CreateContainerResponse, CreateVolumeRequest, CreateVolumeResponse, InspectContainerResponse, InspectImageResult, InspectVolumeResponse, ListContainerRequestFilter, ListContainerResponseRow, ListVolumeResponse, RemoveImageRow, VersionResponse }
import io.circe.{ Decoder, Encoder, Json }
import net.reactivecore.fhttp.{ ApiBuilder, Input, Output }

import scala.util.Try
import io.circe.syntax._

/** Implements (parts) of the Docker API. */
object DockerApi extends ApiBuilder {

  val version = add(
    get("version")
      .responding(
        jsonResponseWithErrorHandlung[VersionResponse]
      )
  )

  val createContainer = add(
    post("containers", "create")
      .expecting(input.AddQueryParameter("name"))
      .expecting(input.circe[CreateContainerRequest]())
      .responding(
        jsonResponseWithErrorHandlung[CreateContainerResponse]
      )
  )

  val inspectContainer = add(
    get("containers")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("json")))
      .responding(
        jsonResponseWithErrorHandlung[InspectContainerResponse]
      )
  )

  val boolMapping = Input.pureMapping[String, Boolean](
    { s => Try(s.toBoolean).toEither.left.map(_.toString) },
    { b => Right(b.toString) }
  )

  val listContainers = add(
    get("containers", "json")
      .expecting(
        input.MappedInput(input.AddQueryParameter("all"), boolMapping)
      )
      .responding(
        jsonResponseWithErrorHandlung[List[ListContainerResponseRow]]
      )
  )

  val filterMapping = Input.pureMapping[String, ListContainerRequestFilter](
    { s => io.circe.parser.parse(s).flatMap(_.as[ListContainerRequestFilter]).left.map(_.toString) },
    { f => Right(f.asJson.noSpaces) }
  )

  val listContainersFiltered = add(
    get("containers", "json")
      .expecting(
        input.MappedInput(input.AddQueryParameter("all"), boolMapping)
      )
      .expecting(
        input.MappedInput(input.AddQueryParameter("filters"), filterMapping)
      )
      .responding(
        jsonResponseWithErrorHandlung[Vector[ListContainerResponseRow]]
      )
  )

  val killContainer = add(
    post("containers")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("kill")))
      .responding(
        respondWithErrorHandling(Output.Empty)
      )
  )

  val removeContainer = add(
    delete("containers")
      .expecting(input.ExtraPath)
      .expecting(input.MappedInput(input.AddQueryParameter("force"), boolMapping))
      responding (
        respondWithErrorHandling(Output.Empty)
      )
  )

  val startContainer = add(
    post("containers")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("start")))
      .responding(
        respondWithErrorHandling(Output.Empty)
      )
  )

  val containerLogs = add(
    get("containers")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("logs")))
      .expecting(input.MappedInput(input.AddQueryParameter("stdout"), boolMapping))
      .expecting(input.MappedInput(input.AddQueryParameter("stderr"), boolMapping))
      .responding(
        // Docker responds with application/octet-stream instead of text.
        respondWithErrorHandling(Output.Binary)
      )
  )

  val containerWait = add(
    post("containers")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("wait")))
      .responding(
        jsonResponseWithErrorHandlung[ContainerWaitResponse]
      )
  )

  // Note: the pull operation fails, if the (empty) content is not consumed!
  val pullImage = add(
    post("images", "create")
      .expecting(input.AddQueryParameter("fromImage"))
      .responding(
        respondWithErrorHandling(Output.Binary)
      )
  )

  val removeImage = add(
    delete("images")
      .expecting(input.ExtraPath)
      .responding(
        jsonResponseWithErrorHandlung[Vector[RemoveImageRow]]
      )
  )

  val inspectImage = add(
    get("images")
      .expecting(input.ExtraPath)
      .expecting(input.ExtraPathFixed(List("json")))
      .responding(
        jsonResponseWithErrorHandlung[InspectImageResult]
      )
  )

  val createVolume = add(
    post("volumes", "create")
      .expecting(input.circe[CreateVolumeRequest]())
      .responding(
        jsonResponseWithErrorHandlung[CreateVolumeResponse]
      )
  )

  val removeVolume = add(
    delete("volumes")
      .expecting(input.ExtraPath)
      .responding(
        respondWithErrorHandling(output.Empty)
      )
  )

  val inspectVolume = add(
    get("volumes")
      .expecting(input.ExtraPath)
      .responding(
        jsonResponseWithErrorHandlung[InspectVolumeResponse]
      )
  )

  val listVolumes = add(
    get("volumes")
      .responding(
        jsonResponseWithErrorHandlung[ListVolumeResponse]
      )
  )

  private def respondWithErrorHandling[T <: Output](success: T) = Output.ErrorSuccess(
    output.circe[ErrorResponse](),
    success
  )

  private def jsonResponseWithErrorHandlung[T](implicit encoder: Encoder[T], decoder: Decoder[T]) = {
    respondWithErrorHandling(output.circe[T]())
  }
}
