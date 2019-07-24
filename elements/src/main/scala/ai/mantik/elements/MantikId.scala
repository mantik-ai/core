package ai.mantik.elements

import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json }

import scala.util.matching.Regex

/**
 * Identifies a Mantik Artifact.
 *
 * @param name of the Mantik artifact. If it starts with @ it refers to a [[ItemId]].
 */
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

  /** Returns true if the Mantik Id is generated. */
  def isAnonymous: Boolean = name.startsWith(MantikId.AnonymousPrefix)

  /** Returns Naming violatins. */
  def violations: Seq[String] = MantikId.nameViolations(name) ++ MantikId.versionViolations(version)
}

object MantikId {
  import scala.language.implicitConversions

  /** If no version is given, this version is accessed. */
  val DefaultVersion = "latest"

  /** Prefix for Anonymous Mantik Ids. */
  val AnonymousPrefix = "@"

  /** Automatic conversion from strings. */
  implicit def fromString(s: String): MantikId = {
    decodeString(s) match {
      case Left(e)  => throw new IllegalArgumentException(e.getMessage())
      case Right(v) => v
    }
  }

  private def decodeString(s: String): Result[MantikId] = {
    s.split(":").toList match {
      case List(name, version) => Right(MantikId(name, version))
      case List(name)          => Right(MantikId(name))
      case _ =>
        Left(DecodingFailure(s"${s} is not a valid Mantik id", Nil))
    }
  }

  def anonymous(itemId: ItemId): MantikId = MantikId(AnonymousPrefix + itemId.toString)

  /** Encodes a mantik id within a string. */
  implicit val mantikIdCodec: Encoder[MantikId] with Decoder[MantikId] = new Encoder[MantikId] with Decoder[MantikId] {
    override def apply(a: MantikId): Json = {
      Json.fromString(a.toString)
    }

    override def apply(c: HCursor): Result[MantikId] = {
      for {
        s <- c.as[String]
        r <- decodeString(s)
      } yield r
    }
  }

  /** Validates the name, returns violations. */
  def nameViolations(name: String): Seq[String] = {
    if (name.startsWith("@")) {
      if (!AnonymousNameRegex.pattern.matcher(name).matches()) {
        return Seq("Invalid anonymous name")
      }
      return Nil
    }
    val violations = Seq.newBuilder[String]
    if (!NameRegex.pattern.matcher(name).matches()) {
      violations += "Invalid Name"
    }
    violations.result()
  }

  /** Validates the version, returns violations. */
  def versionViolations(version: String): Seq[String] = {
    val violations = Seq.newBuilder[String]
    if (!VersionRegex.pattern.matcher(version).matches()) {
      violations += "Invalid Version"
    }
    violations.result()
  }

  /**
   * Regex for a Name.
   * Note: in contrast to account names, also "_" and "." in the middle is allowed
   */
  val NameRegex: Regex = "^[a-z\\d](?:[a-z\\d_\\.]|-(?=[a-z\\d])){0,50}$".r

  /** Regex for anonymous names. */
  val AnonymousNameRegex: Regex = "^@[a-zA-Z-_\\d]{1,50}$".r

  /**
   * Regex for a Version.
   * Note: in contrast to account names, also "_" and "." in the middle is allowed
   */
  val VersionRegex: Regex = "^[a-z\\d]([a-z\\d_\\.\\-]*[a-z\\d])?$".r
}