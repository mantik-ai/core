package ai.mantik.elements

import java.security.SecureRandom
import java.util.Base64

import io.circe.Decoder.Result
import io.circe.{ Decoder, DecodingFailure, Encoder, HCursor, Json }

/**
 * Provides a stable identifier for Mantik Items.
 *
 * In contrast to [[MantikId]] this id always responds to the very same
 * item. In practice, all [[MantikId]] link to a [[ItemId]] which link
 * to the item content.
 *
 * They can not be overwritten but deleted.
 */
case class ItemId(
    private val id: String
) {
  override def toString: String = s"${id}"

  def asAnonymousMantikId: MantikId = MantikId.anonymous(this)
}

object ItemId {
  val ByteCount = 32

  private val generator = new SecureRandom()

  def generate(): ItemId = {
    val value = new Array[Byte](ByteCount)
    generator.nextBytes(value)
    ItemId(encodeBinary(value))
  }

  def fromString(s: String): ItemId = {
    ItemId(s)
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