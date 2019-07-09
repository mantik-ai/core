package ai.mantik.elements

import java.util.UUID

import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json }

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
}