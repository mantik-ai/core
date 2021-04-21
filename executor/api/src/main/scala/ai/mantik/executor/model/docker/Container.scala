package ai.mantik.executor.model.docker

import ai.mantik.componently.utils.Renderable
import com.typesafe.config.{Config, ConfigException}
import io.circe.generic.JsonCodec

/** Defines how a container is going to be started. */
@JsonCodec
case class Container(
    image: String,
    parameters: Seq[String] = Nil
) {

  /** Returns the docker image tag. */
  def imageTag: Option[String] = {
    Container.splitImageRepoTag(image).map(_._2)
  }

  /** Returns the image name without repo or tag. */
  def simpleImageName: String = {
    val slashIdx = image.indexOf('/')
    val withoutRepo = slashIdx match {
      case -1 => image
      case n  => image.substring(n + 1)
    }
    val tagIdx = withoutRepo.indexOf(':')
    tagIdx match {
      case -1 => withoutRepo
      case n  => withoutRepo.substring(0, n)
    }
  }
}

object Container {

  /** Strip tag from image. If there is no tag, returns None */
  def splitImageRepoTag(imageName: String): Option[(String, String)] = {
    val slashIdx = imageName.indexOf('/')
    val tagIdx = imageName.indexOf(':', slashIdx + 1)
    if (tagIdx > 0) {
      Some(imageName.take(tagIdx) -> imageName.drop(tagIdx + 1))
    } else {
      None
    }
  }

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

  implicit val renderable = Renderable.makeRenderable[Container] { c =>
    Renderable.keyValueList(
      "Container",
      "image" -> c.image,
      "parameters" -> c.parameters
    )
  }
}
