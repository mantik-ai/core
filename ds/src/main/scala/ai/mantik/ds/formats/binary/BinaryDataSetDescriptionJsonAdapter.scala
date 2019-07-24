package ai.mantik.ds.formats.binary

import ai.mantik.ds.helper.circe.{ CirceJson, EnumDiscriminatorCodec, TrialDependentCodec }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }
import io.circe.{ Decoder, ObjectEncoder }

/**
 * Encodes BinaryDataSet Descriptions.
 */
@deprecated("Binary decoding should be done by binary bridge.")
private[binary] object BinaryDataSetDescriptionJsonAdapter {

  implicit object compressionCodec extends EnumDiscriminatorCodec[Compression](
    Seq("gzip" -> Compression.Gzip)
  )

  implicit val binaryFileContentCodec = new TrialDependentCodec[BinaryFileContent] {
    val subTypes = Seq(
      makeSubType[BinaryFileContent.Stride](),
      makeSubType[BinaryFileContent.Skip](),
      makeSubType[BinaryFileContent.Element]()
    )
  }

  implicit val binaryFileDescriptionEncoder: ObjectEncoder[BinaryFileDescription] = deriveEncoder[BinaryFileDescription]
    .mapJsonObject(CirceJson.stripNullValues)

  implicit val binaryFileDescriptionDecoder: Decoder[BinaryFileDescription] = deriveDecoder[BinaryFileDescription]

  implicit val binaryDataSetEncoder: ObjectEncoder[BinaryDataSetDescription] = deriveEncoder[BinaryDataSetDescription]
    .mapJsonObject(CirceJson.stripNullValues)

  implicit val binaryDataSetDecoder: Decoder[BinaryDataSetDescription] = deriveDecoder[BinaryDataSetDescription]

}
