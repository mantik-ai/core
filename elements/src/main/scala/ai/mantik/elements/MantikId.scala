package ai.mantik.elements

import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json }

import scala.util.matching.Regex

/**
 * Identifies a Mantik Artifact.
 *
 * @param account
 * @param name of the Mantik artifact. If it starts with @ it refers to a [[ItemId]].
 */
case class MantikId(
    // Note: ordering is this way, to force the user to use
    // named arguments to set name explicitly.
    // There is also an implicit conversion from string
    // which parses all elements and an apply function for conversion from string.
    account: String = MantikId.DefaultAccount,
    name: String,
    version: String = MantikId.DefaultVersion
) {

  override def toString: String = {
    val builder = StringBuilder.newBuilder
    if (account != MantikId.DefaultAccount) {
      builder ++= account
      builder += '/'
    }
    builder ++= name
    if (version != MantikId.DefaultVersion) {
      builder += ':'
      builder ++= version
    }
    return builder.result()
  }

  /** Returns true if the Mantik Id is generated. */
  def isAnonymous: Boolean = name.startsWith(MantikId.AnonymousPrefix)

  /** Returns Naming violatins. */
  def violations: Seq[String] = MantikId.accountViolations(account) ++ MantikId.nameViolations(name) ++ MantikId.versionViolations(version)
}

object MantikId {
  import scala.language.implicitConversions

  /** If no version is given, this version is accessed. */
  val DefaultVersion = "latest"

  /** The library account is the default and omitted on serializing. */
  val DefaultAccount = "library"

  /** Prefix for Anonymous Mantik Ids. */
  val AnonymousPrefix = "@"

  /** Automatic conversion from strings. */
  implicit def fromString(s: String): MantikId = {
    decodeString(s) match {
      case Left(e)  => throw new IllegalArgumentException(e.getMessage())
      case Right(v) => v
    }
  }

  def apply(s: String): MantikId = fromString(s)

  /** JSON Encoding to String. */
  implicit val encoder: Encoder[MantikId] = new Encoder[MantikId] {
    override def apply(a: MantikId): Json = Json.fromString(a.toString)
  }

  /** JSON Encoding from String. */
  implicit val decoder: Decoder[MantikId] = new Decoder[MantikId] {
    override def apply(c: HCursor): Result[MantikId] = {
      c.value.asString match {
        case None => Left(DecodingFailure("Expected string", c.history))
        case Some(s) =>
          decodeString(s)
      }
    }
  }

  private def decodeString(s: String): Result[MantikId] = {
    s.split(":").toList match {
      case List(accountName, version) =>
        decodeAccountName(accountName).map {
          case (account, name) =>
            MantikId(account = account, name = name, version = version)
        }
      case List(accountName) =>
        decodeAccountName(accountName).map {
          case (account, name) =>
            MantikId(
              name = name,
              account = account,
              version = MantikId.DefaultVersion
            )
        }
      case _ =>
        Left(DecodingFailure(s"${s} is not a valid Mantik id", Nil))
    }
  }

  private def decodeAccountName(s: String): Result[(String, String)] = {
    s.split("/").toList match {
      case List(account, name) => Right((account, name))
      case List(name)          => Right((DefaultAccount, name))
      case _ =>
        Left(DecodingFailure(s"${s} is not a valid account / name combination", Nil))
    }
  }

  def anonymous(itemId: ItemId): MantikId = MantikId(
    name = AnonymousPrefix + itemId.toString,
    account = MantikId.DefaultAccount,
    version = MantikId.DefaultVersion
  )

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

  /** Regex for account. */
  val AccountRegex: Regex = NameRegex

  /** Regex for anonymous names. */
  val AnonymousNameRegex: Regex = "^@[a-zA-Z-_\\d]{1,50}$".r

  /**
   * Regex for a Version.
   * Note: in contrast to account names, also "_" and "." in the middle is allowed
   */
  val VersionRegex: Regex = "^[a-z\\d]([a-z\\d_\\.\\-]*[a-z\\d])?$".r
}