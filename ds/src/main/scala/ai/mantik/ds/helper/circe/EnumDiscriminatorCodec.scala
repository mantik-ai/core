package ai.mantik.ds.helper.circe

import ai.mantik.ds.helper.circe.EnumDiscriminatorCodec.UnregisteredElementException
import io.circe.Decoder.Result
import io.circe._

/**
  * Encodes the values of a trivial disjunct enum hierarchy into a Json string.
  * Note: is using circe json.
  *
  * TODO: Circe's [[KeyDecoder]] and [[KeyEncoder]] somehow have a similar meaning however
  * they are abstract classes so we cannot easily mix them together. Maybe it's a good idea to clean up.
  */
class EnumDiscriminatorCodec[T](val mapping: Seq[(String, T)]) extends Encoder[T] with Decoder[T] {

  private val DecodeMap: Map[String, T] = mapping.toMap
    .ensuring(_.size == mapping.size, "There may be no duplicates in mapping")

  private val EncodeMap: Map[T, String] = mapping
    .map(x => (x._2, x._1))
    .toMap
    .ensuring(_.size == mapping.size, "There may be no duplicates in mapping")

  def elementToString(element: T): String = {
    EncodeMap.getOrElse(element, throw new UnregisteredElementException(s"Unregistered element ${element}"))
  }

  def stringToElement(name: String): Option[T] = {
    DecodeMap.get(name)
  }

  override def apply(a: T): Json = {
    Json.fromString(elementToString(a))
  }

  override def apply(c: HCursor): Result[T] = {
    c.value.asString match {
      case None => Left(DecodingFailure(s"Expected String", Nil))
      case Some(s) =>
        stringToElement(s) match {
          case None    => Left(DecodingFailure(s"Unknown value ${s}", Nil))
          case Some(v) => Right(v)
        }
    }
  }
}

object EnumDiscriminatorCodec {

  /** An element which is about to serialize wasn't registered. */
  class UnregisteredElementException(msg: String) extends RuntimeException
}
