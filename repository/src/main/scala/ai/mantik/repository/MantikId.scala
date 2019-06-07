package ai.mantik.repository

/** Identifies a [[MantikArtifact]]. */
case class MantikId(
    name: String,
    version: String = MantikId.DefaultVersion
) {

  override def toString: String = {
    if (version == MantikId.DefaultVersion) {
      name
    } else {
      name + ":" + version
    }
  }
}

object MantikId {
  import scala.language.implicitConversions

  /** If no version is given, this version is accessed. */
  val DefaultVersion = "latest"

  /** Automatic conversion from strings. */
  implicit def fromString(s: String): MantikId = {
    s.split(":").toList match {
      case List(name, version) => MantikId(name, version)
      case List(name)          => MantikId(name)
      case somethingElse =>
        throw new IllegalArgumentException(s"${s} is not a valid Mantik id")
    }
  }
}