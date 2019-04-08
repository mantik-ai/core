package ai.mantik.planner.plugins

/** A Plugin for an algorithm stack. */
trait AlgorithmPlugin {

  /** The name of the stack. */
  def stack: String

  /** Returns the name of the container image which executes the algorithm data transformation. */
  def transformationContainerImage: String
}

object TensorFlowSavedModelPlugin extends AlgorithmPlugin {
  override def stack: String = "tf.saved_model"

  override def transformationContainerImage: String = {
    "tf.savedmodel"
  }
}
