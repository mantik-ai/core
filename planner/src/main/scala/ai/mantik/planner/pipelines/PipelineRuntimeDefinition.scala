package ai.mantik.planner.pipelines

import ai.mantik.ds.DataType
import io.circe.generic.JsonCodec

/**
  * Runtime definition of pipelines.
  * Must be compatible with the Golang Pipeline controller definition.
  */
@JsonCodec
case class PipelineRuntimeDefinition(
    name: String,
    steps: Seq[PipelineRuntimeDefinition.Step],
    inputType: DataType
)

object PipelineRuntimeDefinition {
  @JsonCodec
  case class Step(
      url: String,
      outputType: DataType
  )
}
