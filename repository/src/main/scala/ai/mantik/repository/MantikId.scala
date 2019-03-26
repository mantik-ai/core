package ai.mantik.repository

/** Identifies a [[MantikArtefact]]. */
case class MantikId(
    name: String,
    version: Option[String] = None
) {

  override def toString: String = {
    version match {
      case Some(v) => name + ":" + v
      case None    => name
    }
  }
}

object MantikId {
  import scala.language.implicitConversions

  /** Automatic conversion from strings. */
  implicit def fromString(s: String): MantikId = {
    s.split(":").toList match {
      case List(name, version) => MantikId(name, Some(version))
      case List(name)          => MantikId(name)
      case somethingElse =>
        throw new IllegalArgumentException(s"${s} is not a valid Mantik id")
    }
  }
}