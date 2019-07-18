package ai.mantik.planner.bridge

import ai.mantik.executor.model.docker.{ Container, DockerConfig }
import com.typesafe.config.{ Config, ConfigException, ConfigObject }
import javax.inject.{ Inject, Provider }

import scala.collection.JavaConverters._

/** Contain the information about all configured Bridges. */
trait Bridges {

  /** Lookups an Algorithm Bridge. */
  def algorithmBridge(stack: String): Option[AlgorithmBridge]

  /** Lookups a Trainable Algorithm Bridge. */
  def trainableAlgorithmBridge(stack: String): Option[TrainableAlgorithmBridge]

  /** Lookups a format Bridge. */
  def formatBridge(format: String): Option[FormatBridge]
}

object Bridges {

  /** Load Bridges from Configuration. */
  @throws[ConfigException]
  def loadFromConfig(config: Config): Bridges = new BridgeLoader(config).toBridges
}

private[mantik] case class BridgeList(
    algorithms: Seq[AlgorithmBridge],
    trainableAlgorithms: Seq[TrainableAlgorithmBridge],
    formats: Seq[FormatBridge]
) extends Bridges {

  override def algorithmBridge(stack: String): Option[AlgorithmBridge] =
    algorithms.find(_.stack == stack)

  override def trainableAlgorithmBridge(stack: String): Option[TrainableAlgorithmBridge] =
    trainableAlgorithms.find(_.stack == stack)

  override def formatBridge(format: String): Option[FormatBridge] =
    formats.find(_.format == format)
}

class BridgesProvider @Inject() (config: Config) extends Provider[Bridges] {
  override def get(): Bridges = {
    new BridgeLoader(config).toBridges
  }
}

private[bridge] class BridgeLoader(config: Config) {

  val MainKey = "mantik.bridge"
  val MainConfig = config.getConfig(MainKey)

  val dockerConfig = DockerConfig.parseFromConfig(MainConfig.getConfig("docker"))

  val algorithms: Seq[AlgorithmBridge] = parseBridges("algorithm", parseAlgorithmBridge)
  val trainableAlgorithms: Seq[TrainableAlgorithmBridge] = parseBridges("trainableAlgorithm", parseTrainableAlgorithmBridge)
  val formats: Seq[FormatBridge] = parseBridges("format", parseFormat)

  def toBridges: Bridges = {
    BridgeList(
      algorithms, trainableAlgorithms, formats
    )
  }

  private def parseBridges[T](path: String, generator: (String, Config) => T): Seq[T] = {
    MainConfig.getObject(path).asScala.map {
      case (name, value) =>
        value match {
          case co: ConfigObject => generator(name, co.toConfig)
          case otherwise        => throw new IllegalArgumentException(s"bridge ${path} should be an object, got ${otherwise.valueType()} instead")
        }
    }.toVector
  }

  private def parseAlgorithmBridge(name: String, config: Config): AlgorithmBridge = {
    AlgorithmBridge(name, parseResolvedContainer(config))
  }

  private def parseTrainableAlgorithmBridge(name: String, config: Config): TrainableAlgorithmBridge = {
    TrainableAlgorithmBridge(name, parseResolvedContainer(config))
  }

  private def parseFormat(name: String, config: Config): FormatBridge = {
    val container = if (config.hasPath("container")) {
      Some(parseResolvedContainer(config))
    } else {
      None
    }
    FormatBridge(name, container)
  }

  private def parseResolvedContainer(config: Config): Container = {
    dockerConfig.resolveContainer(Container.parseFromTypesafeConfig(config.getConfig("container")))
  }
}
