package ai.mantik.core.plugins

import java.nio.charset.StandardCharsets
import java.util.Base64

import ai.mantik.core.Planner.{ NotAvailableException, PlannerException }
import ai.mantik.core.plugins.TrainableAlgorithmPlugin.ClusterLearner
import ai.mantik.executor.model.{ Container, ContainerService, Node, ResourceType }
import ai.mantik.repository.{ AlgorithmDefinition, MantikDefinition, Mantikfile, TrainableAlgorithmDefinition }

/** Plugin for trainable algorithms. */
trait TrainableAlgorithmPlugin {

  /** The name of the stack. */
  def stack: String

  /**
   * Generate a node service which represents this trainable algorithm.
   * Returns the node and the resource names for training, stats, and result
   * @param mantikfile the mantik file
   * @param file the remote file to read (URL + Resource) if given
   * @throws PlannerException
   */
  def createClusterLearner(mantikfile: Mantikfile[TrainableAlgorithmDefinition], file: Option[(String, String)]): TrainableAlgorithmPlugin.ClusterLearner
}

object TrainableAlgorithmPlugin {

  /** A trainable Algorithm in the cluster representation. */
  case class ClusterLearner(
      node: Node,
      trainingInput: String,
      trainedOutput: String,
      statsOutput: String
  )

}

/** Plugin for SkLearn Python only bridge, can do both learning and executing. */
object SkLearnSimplePlugin extends TrainableAlgorithmPlugin with AlgorithmPlugin {
  override def stack: String = "sklearn.simple_learn"

  override def createClusterLearner(mantikfile: Mantikfile[TrainableAlgorithmDefinition], file: Option[(String, String)]): TrainableAlgorithmPlugin.ClusterLearner = {
    val containerService = createContainerService(mantikfile, file)

    val trainResource = "train"
    val statsResource = "stats"
    val resultResource = "result"

    val node = Node(
      containerService,
      Map(
        trainResource -> ResourceType.Sink,
        statsResource -> ResourceType.Source,
        resultResource -> ResourceType.Source
      )
    )
    ClusterLearner(
      node,
      trainingInput = trainResource,
      statsOutput = statsResource,
      trainedOutput = resultResource
    )
  }

  override def createClusterTransformation(mantikfile: Mantikfile[AlgorithmDefinition], file: Option[(String, String)]): (Node, String) = {
    val applyResource = "apply"
    val node = Node(
      createContainerService(mantikfile, file),
      Map(
        applyResource -> ResourceType.Transformer
      )
    )
    node -> applyResource
  }

  private def createContainerService(mantikfile: Mantikfile[_ <: MantikDefinition], file: Option[(String, String)]): ContainerService = {
    val (rootUrl, fileResource) = file.getOrElse(
      throw new NotAvailableException("File is not available")
    )
    val dataPayloadUrl = rootUrl + fileResource
    val base64Encoder = Base64.getEncoder
    val encodedMantikFile = base64Encoder.encodeToString(
      mantikfile.json.spaces2.getBytes(StandardCharsets.UTF_8)
    )

    // The file resource contains the payload, however the Tensorflow bridge expects it
    // to be in the Mantikfile directory.

    val payloadDirectory = mantikfile.definition.directory.getOrElse {
      throw new NotAvailableException(s"Plugin doesn't work with payload directory")
    }

    ContainerService(
      main = Container(
        "bridge.sklearn.simple"
      ),
      dataProvider = Some(
        Container(
          "payload_preparer",
          parameters = Seq("-url", dataPayloadUrl, "-mantikfile", encodedMantikFile, "-pdir", payloadDirectory)
        )
      )
    )
  }
}