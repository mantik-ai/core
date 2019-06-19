package ai.mantik.planner.pipelines

/** Something is wrong with a pipeline. */
class PipelineException(msg: String, cause: Throwable = null) extends RuntimeException(msg)

/** A pipeline is invalid. */
class InvalidPipelineException(msg: String, cause: Throwable = null) extends PipelineException(msg)

/** DataTypes do not match within the pipeline. */
class PipelineTypeException(msg: String) extends PipelineException(msg)
