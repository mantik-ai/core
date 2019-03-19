package io.mantik.executor.model

/** Default values for Executor Model. */
object ExecutorModelDefaults {
  /** Default port for Mantik containers. */
  val Port = 8502

  /** Resource for simple sources. */
  val SourceResource = "out"

  /** Resource for simple sinks. */
  val SinkResource = "in"

  /** Resource for simple transformations (e.g. Algorithms). */
  val TransformationResource = "apply"
}
