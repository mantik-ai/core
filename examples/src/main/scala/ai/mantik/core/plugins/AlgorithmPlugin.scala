package ai.mantik.core.plugins

import java.nio.charset.StandardCharsets
import java.util.Base64

import ai.mantik.core.Planner.{ NotAvailableException, PlannerException }
import ai.mantik.executor.model.{ Container, ContainerService, Node, ResourceType }
import ai.mantik.repository.{ AlgorithmDefinition, Mantikfile }

/** A Plugin for an algorithm stack. */
trait AlgorithmPlugin {

  /** The name of the stack. */
  def stack: String

  /**
   * Generate a node service which calculates this algorithm.
   * Returns the node and the resource for transformation
   * @param mantikfile the mantik file
   * @param file the remote file to read (URL + Resource) if given
   * @throws PlannerException
   */
  def createClusterTransformation(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[(String, String)]): (Node, String)

}

object TensorFlowSavedModelPlugin extends AlgorithmPlugin {
  override def stack: String = "tf.saved_model"

  override def createClusterTransformation(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[(String, String)]): (Node, String) = {
    val (rootUrl, fileResource) = file.getOrElse(
      throw new NotAvailableException("File is not available")
    )
    val dataPayloadUrl = rootUrl + fileResource
    val base64Encoder = Base64.getEncoder
    val encodedMantikFile = base64Encoder.encodeToString(
      mantikfile.json.spaces2.getBytes(StandardCharsets.UTF_8)
    )

    val resource = "apply" // See tf bridge

    // The file resource contains the payload, however the Tensorflow bridge expects it
    // to be in the Mantikfile directory.

    val payloadDirectory = mantikfile.definition.directory.getOrElse {
      throw new NotAvailableException(s"Saved Model doesn't work with payload directory")
    }

    val node = Node(
      ContainerService(
        main = Container(
          "tf.savedmodel"
        ),
        dataProvider = Some(
          Container(
            "payload_preparer",
            parameters = Seq("-url", dataPayloadUrl, "-mantikfile", encodedMantikFile, "-pdir", payloadDirectory)
          )
        )
      ),
      Map(
        resource -> ResourceType.Transformer
      )
    )
    node -> resource
  }
}
