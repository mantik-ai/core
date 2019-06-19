package ai.mantik.repository

import scala.concurrent.Future
import scala.reflect.ClassTag

/** A Mantik Artefact. */
case class MantikArtifact(
    mantikfile: Mantikfile[_ <: MantikDefinition],
    fileId: Option[String],
    id: MantikId
) {
  /** Force a mantik file cast. */
  def forceMantikfileCast[T <: MantikDefinition: ClassTag]: Mantikfile[T] = {
    mantikfile.cast[T] match {
      case Left(error) => throw new Errors.WrongTypeException(error.getMessage)
      case Right(ok)   => ok
    }
  }
}