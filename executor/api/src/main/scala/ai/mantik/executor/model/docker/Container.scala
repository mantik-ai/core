package ai.mantik.executor.model.docker

import com.typesafe.config.{ Config, ConfigException }
import io.circe.generic.JsonCodec

/** Defines how a container is going to be started. */
@JsonCodec
case class Container(
    image: String,
    parameters: Seq[String] = Nil
) {

  /** Returns the docker image tag. */
  def imageTag: Option[String] = {
    val slashIdx = image.indexOf('/')
    val tagIdx = image.indexOf(':', slashIdx + 1)
    if (tagIdx > 0) {
      Some(image.substring(tagIdx + 1))
    } else {
      None
    }
  }
}

object Container {

  /** Parses a Container from Typesafe Config. */
  @throws[ConfigException]
  def parseFromTypesafeConfig(config: Config): Container = {
    import scala.collection.JavaConverters._

    val parameters = if (config.hasPath("parameters")) {
      config.getStringList("parameters").asScala
    } else {
      Nil
    }

    Container(
      image = config.getString("image"),
      parameters = parameters
    )
  }
}