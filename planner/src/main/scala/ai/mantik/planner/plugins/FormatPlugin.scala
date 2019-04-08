package ai.mantik.planner.plugins

import ai.mantik.executor.model.{ ExistingService, Node, ResourceType }
import ai.mantik.planner.Planner.{ NotAvailableException, PlannerException }
import ai.mantik.repository.{ DataSetDefinition, Mantikfile }

/** A Plugin for a dataformat. */
trait FormatPlugin {

  /** The name of the format. */
  def format: String

  /**
   * Generate a node service which reads this artefact.
   * Returns the node and the resource to read.
   * @param mantikfile the mantik file
   * @param file the remote file to read (URL + Resource) if given
   * @throws PlannerException
   */
  def createClusterReader(mantikfile: Mantikfile[DataSetDefinition], file: Option[(String, String)]): (Node, String)

}

object NaturalFormatPlugin extends FormatPlugin {
  override val format: String = "natural"

  override def createClusterReader(mantikfile: Mantikfile[DataSetDefinition], file: Option[(String, String)]): (Node, String) = {
    val (rootUrl, resource) = file.getOrElse(
      throw new NotAvailableException("File is not available")
    )
    val node = Node(
      service = ExistingService(
        rootUrl
      ),
      resources = Map(resource -> ResourceType.Source)
    )
    node -> resource
  }
}
