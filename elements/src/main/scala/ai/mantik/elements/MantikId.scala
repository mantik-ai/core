package ai.mantik.elements

import java.security.SecureRandom
import java.util.Base64

import ai.mantik.elements.errors.InvalidMantikIdException
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json}
import io.circe.syntax._

import scala.util.matching.Regex
import scala.language.implicitConversions

/**
  * Identifies a MantikArtifact. Can either be an (anonymous) ItemId or a
  * [[NamedMantikId]]
  */
sealed trait MantikId {

  /** Converts to String representation. */
  def toString: String
}

object MantikId {
  implicit val encoder: Encoder[MantikId] = new Encoder[MantikId] {
    override def apply(a: MantikId): Json = {
      Json.fromString(a.toString)
    }
  }

  implicit val decoder: Decoder[MantikId] = new Decoder[MantikId] {
    override def apply(c: HCursor): Result[MantikId] = {
      c.value.asString match {
        case None => Left(DecodingFailure("Expected strng", c.history))
        case Some(s) =>
          decodeString(s).left.map(_.wrapInDecodingFailure)
      }
    }
  }

  /** Automatic conversion from strings. */
  implicit def fromString(s: String): MantikId = decodeString(s).fold(e => throw e, identity)

  /** Decode a String into a MantikId. */
  def decodeString(s: String): Either[InvalidMantikIdException, MantikId] = {
    if (s.startsWith(ItemId.ItemIdPrefix)) {
      Right(ItemId.fromString(s))
    } else {
      NamedMantikId.decodeString(s)
    }
  }
}

/**
  * Provides a stable identifier for Mantik Items.
  *
  * In contrast to [[NamedMantikId]] this id always responds to the very same
  * item. In practice, all [[NamedMantikId]] link to a [[ItemId]] which link
  * to the item content.
  *
  * They can not be overwritten but deleted.
  *
  * In the moment they are random, but in future they shall represent hash values
  * of the items.
  */
final class ItemId private (
    private val id: String
) extends MantikId {
  override def toString: String = ItemId.ItemIdPrefix + id

  override def hashCode(): Int = id.hashCode

  override def equals(that: Any): Boolean = {
    that match {
      case that: ItemId => id == that.id
      case _            => false
    }
  }
}

object ItemId {
  val ByteCount = 32

  /** Prefix for string serialization of ItemIds. */
  val ItemIdPrefix = "@"

  private val generator = new SecureRandom()

  def generate(): ItemId = {
    val value = new Array[Byte](ByteCount)
    generator.nextBytes(value)
    new ItemId(encodeBinary(value))
  }

  def apply(s: String): ItemId = {
    fromString(s)
  }

  def fromString(s: String): ItemId = {
    require(s.startsWith(ItemIdPrefix), s"String encoding must start with ${ItemIdPrefix}")
    new ItemId(s.stripPrefix(ItemIdPrefix))
  }

  private def encodeBinary(array: Array[Byte]): String = {
    // Use URL Encoding, we do not want "/"
    Base64.getUrlEncoder.withoutPadding().encodeToString(array)
  }

  implicit val encoder: Encoder[ItemId] = new Encoder[ItemId] {
    override def apply(a: ItemId): Json = Json.fromString(a.toString)
  }

  implicit val decoder: Decoder[ItemId] = new Decoder[ItemId] {
    override def apply(c: HCursor): Result[ItemId] = c.value.asString match {
      case None    => Left(DecodingFailure("Expected string", c.history))
      case Some(s) => Right(fromString(s))
    }
  }
}

/**
  * A Named Mantik Artifact.
  *
  * @param account the users account, defaults to library.
  * @param name of the Mantik artifact. If it starts with @ it refers to a [[ItemId]].
  * @param version the version, defaults to latest.
  */
final case class NamedMantikId(
    // Note: ordering is this way, to force the user to use
    // named arguments to set name explicitly.
    // There is also an implicit conversion from string
    // which parses all elements and an apply function for conversion from string.
    account: String = NamedMantikId.DefaultAccount,
    name: String,
    version: String = NamedMantikId.DefaultVersion
) extends MantikId {

  override def toString: String = {
    val builder = StringBuilder.newBuilder
    if (account != NamedMantikId.DefaultAccount) {
      builder ++= account
      builder += '/'
    }
    builder ++= name
    if (version != NamedMantikId.DefaultVersion) {
      builder += ':'
      builder ++= version
    }
    return builder.result()
  }

  /** Returns Naming violatins. */
  def violations: Seq[String] =
    NamedMantikId.accountViolations(account) ++ NamedMantikId.nameViolations(name) ++ NamedMantikId.versionViolations(
      version
    )
}

object NamedMantikId {

  /** If no version is given, this version is accessed. */
  val DefaultVersion = "latest"

  /** The library account is the default and omitted on serializing. */
  val DefaultAccount = "library"

  /** Automatic conversion from strings. */
  @throws[InvalidMantikIdException]("On invalid Mantik Ids")
  implicit def fromString(s: String): NamedMantikId = {
    decodeString(s) match {
      case Left(e)  => throw e
      case Right(v) => v
    }
  }

  @throws[InvalidMantikIdException]("On invalid Mantik Ids")
  def apply(s: String): NamedMantikId = fromString(s)

  /** JSON Encoding to String. */
  implicit val encoder: Encoder[NamedMantikId] = new Encoder[NamedMantikId] {
    override def apply(a: NamedMantikId): Json = Json.fromString(a.toString)
  }

  /** JSON Encoding from String. */
  implicit val decoder: Decoder[NamedMantikId] = new Decoder[NamedMantikId] {
    override def apply(c: HCursor): Result[NamedMantikId] = {
      c.value.asString match {
        case None => Left(DecodingFailure("Expected string", c.history))
        case Some(s) =>
          decodeString(s).left.map(_.wrapInDecodingFailure)
      }
    }
  }

  /** Decodes NamedMantikId with error handling. */
  def decodeStringResult(s: String): Result[NamedMantikId] = {
    s.split(":").toList match {
      case List(accountName, version) =>
        decodeAccountName(accountName).map { case (account, name) =>
          NamedMantikId(account = account, name = name, version = version)
        }
      case List(accountName) =>
        decodeAccountName(accountName).map { case (account, name) =>
          NamedMantikId(
            name = name,
            account = account,
            version = NamedMantikId.DefaultVersion
          )
        }
      case _ =>
        Left(DecodingFailure(s"${s} is not a valid Mantik id", Nil))
    }
  }

  /** Like decodeStringResult but encodes errors in InvalidMantikIdException. */
  def decodeString(s: String): Either[InvalidMantikIdException, NamedMantikId] = {
    decodeStringResult(s).left.map(InvalidMantikIdException.fromDecodingFailure)
  }

  private def decodeAccountName(s: String): Result[(String, String)] = {
    s.split("/").toList match {
      case List(account, name) => Right((account, name))
      case List(name)          => Right((DefaultAccount, name))
      case _ =>
        Left(DecodingFailure(s"${s} is not a valid account / name combination", Nil))
    }
  }

  /** Encodes a mantik id within a string. */
  implicit val mantikIdCodec: Encoder[NamedMantikId] with Decoder[NamedMantikId] = new Encoder[NamedMantikId]
    with Decoder[NamedMantikId] {
    override def apply(a: NamedMantikId): Json = {
      Json.fromString(a.toString)
    }

    override def apply(c: HCursor): Result[NamedMantikId] = {
      for {
        s <- c.as[String]
        r <- decodeStringResult(s)
      } yield r
    }
  }

  /** Validates the account, returns violations. */
  def accountViolations(account: String): Seq[String] = {
    val violations = Seq.newBuilder[String]
    if (!AccountRegex.pattern.matcher(account).matches()) {
      violations += "Invalid Account"
    }
    violations.result()
  }

  /** Validates the name, returns violations. */
  def nameViolations(name: String): Seq[String] = {
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

  /** Regex for account. */
  val AccountRegex: Regex = NameRegex

  /**
    * Regex for a Version.
    * Note: in contrast to account names, also "_" and "." in the middle is allowed
    */
  val VersionRegex: Regex = "^[a-z\\d]([a-z\\d_\\.\\-]*[a-z\\d])?$".r
}
