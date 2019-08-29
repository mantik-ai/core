package ai.mantik.planner

import ai.mantik.planner.impl.exec.FileRepositoryServerRemotePresence
import ai.mantik.planner.repository.FileRepositoryServer
import akka.http.scaladsl.model.{ IllegalUriException, Uri }
import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json }
import io.circe.generic.JsonCodec
import javax.inject.{ Inject, Provider }

/**
 * Information provided by the Engine Server to the engine client.
 * This information should be reduced to a minimum as it breaks
 * separation into modules.
 *
 * @param remoteFileRepositoryAddress the address of the FileRepositoryServer, mapped inside the Executor.
 */
@JsonCodec
case class ClientConfig(
    remoteFileRepositoryAddress: Uri
)

/** Provides a ClientConfig on server side. */
private[planner] class ClientConfigProvider @Inject() (
    fileRepositoryServer: FileRepositoryServer,
    fileRepositoryServerRemotePresence: FileRepositoryServerRemotePresence
) extends Provider[ClientConfig] {
  override def get(): ClientConfig = {
    ClientConfig(
      remoteFileRepositoryAddress = fileRepositoryServerRemotePresence.assembledRemoteUri()
    )
  }
}

object ClientConfig {
  implicit val uriEncoder: Encoder[Uri] = new Encoder[Uri] {
    override def apply(a: Uri): Json = Json.fromString(a.toString)
  }

  implicit val uriDecoder: Decoder[Uri] = new Decoder[Uri] {
    override def apply(c: HCursor): Result[Uri] = {
      c.value.asString match {
        case None => Left(DecodingFailure("Expected String", c.history))
        case Some(v) => try {
          Right(Uri(v))
        } catch {
          case i: IllegalUriException =>
            Left(DecodingFailure(s"Illegal URI ${i.info.summary}", c.history))
        }
      }
    }
  }
}