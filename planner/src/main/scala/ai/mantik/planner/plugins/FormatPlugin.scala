package ai.mantik.planner.plugins

import ai.mantik.repository.{ DataSetDefinition, Mantikfile }

/** A Plugin for a data format. */
trait FormatPlugin {

  /** The name of the format. */
  def format: String

  /**
   * Returns the cluster reader container image.
   * @return None if no container is needed, then the data will be directly piped (only natural format).
   */
  def clusterReaderContainerImage(mantikfile: Mantikfile[DataSetDefinition]): Option[String]
}

object NaturalFormatPlugin extends FormatPlugin {
  override val format: String = "natural"

  override def clusterReaderContainerImage(mantikfile: Mantikfile[DataSetDefinition]): Option[String] = None
}
