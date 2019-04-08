package ai.mantik.planner.plugins

/** Plugin for trainable algorithms. */
trait TrainableAlgorithmPlugin {

  /** The name of the stack. */
  def stack: String

  /** Returns the name of the container image. */
  def trainableContainerImage: String
}

/** Plugin for SkLearn Python only bridge, can do both learning and executing. */
object SkLearnSimplePlugin extends TrainableAlgorithmPlugin with AlgorithmPlugin {
  override def stack: String = "sklearn.simple_learn"

  override def trainableContainerImage: String = {
    "bridge.sklearn.simple"
  }

  override def transformationContainerImage: String = {
    // Is the same
    trainableContainerImage
  }
}