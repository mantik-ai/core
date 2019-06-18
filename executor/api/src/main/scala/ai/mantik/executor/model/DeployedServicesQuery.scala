package ai.mantik.executor.model

import io.circe.Decoder.Result
import io.circe.{ Decoder, ObjectEncoder }
import io.circe.syntax._
import io.circe.generic.{ JsonCodec, semiauto }

/** Query for deployed services. */
case class DeployedServicesQuery(
    isolationSpace: String,
    serviceId: Option[String] = None,
    serviceName: Option[String] = None
) {

  /** Convert the query into a http query parameter list. */
  def toQueryParameters: List[(String, String)] = {
    (this: DeployedServicesQuery).asJsonObject.toList.filterNot(_._2.isNull).map {
      case (key, value) =>
        value.asString match {
          case Some(s) => key -> s
          case None    => key -> value.toString()
        }
    }
  }
}

object DeployedServicesQuery {
  implicit val encoder: ObjectEncoder[DeployedServicesQuery] = semiauto.deriveEncoder[DeployedServicesQuery]
  implicit val decoder: Decoder[DeployedServicesQuery] = semiauto.deriveDecoder[DeployedServicesQuery]

  /** Decode the query from http query parameters. */
  def fromQueryParameters(queryParameters: Map[String, String]): Result[DeployedServicesQuery] = {
    queryParameters.asJson.as[DeployedServicesQuery]
  }
}

/** Response for [[DeployedServicesQuery]]. */
@JsonCodec
case class DeployedServicesResponse(
    services: Seq[DeployedServicesEntry]
)

/** A Single Entry within [[DeployedServicesResponse]]. */
@JsonCodec
case class DeployedServicesEntry(
    serviceId: String,
    serviceName: String,
    serviceUrl: String
)

