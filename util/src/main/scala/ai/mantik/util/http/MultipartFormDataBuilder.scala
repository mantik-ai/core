package ai.mantik.util.http

import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.{ HttpHeader, MessageEntity, Multipart }
import akka.stream.scaladsl.Source
import akka.util.ByteString

/** Builder for Form Data. */
case class MultipartFormDataBuilder(
    parts: Vector[Multipart.FormData.BodyPart] = Vector.empty
) {

  /** Add a simple string body part. */
  def addString(name: String, value: String): MultipartFormDataBuilder = {
    copy(
      parts = parts :+ Multipart.FormData.BodyPart(name, value)
    )
  }

  /**
   * Add a binary stream body part.
   * @param fileName if given, the part will be interpreted as file part (at least from play).
   */
  def addBinaryStream(name: String, contentType: String, source: Source[ByteString, _], fileName: Option[String] = None): MultipartFormDataBuilder = {
    val ct = SimpleHttpClient.lookupBinaryContentType(contentType)
    val entity = IndefiniteLength(ct, source)
    val extraHeader = fileName.map { fileName =>
      Map("filename" -> fileName)
    }.getOrElse(Map.empty)
    copy(
      parts = parts :+ Multipart.FormData.BodyPart(name, entity, _additionalDispositionParams = extraHeader)
    )
  }

  /** Converts the result to a MessageEntity. */
  def toEntity(): MessageEntity = Multipart.FormData(parts: _*).toEntity()
}