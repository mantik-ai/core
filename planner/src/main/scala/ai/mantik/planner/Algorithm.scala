package ai.mantik.planner

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.repository.{ AlgorithmDefinition, Mantikfile }

/** Some A => B Transformation Algorithm */
case class Algorithm(
    source: Source,
    mantikfile: Mantikfile[AlgorithmDefinition]
) extends MantikItem {

  override type DefinitionType = AlgorithmDefinition

  def functionType: FunctionType = mantikfile.definition.`type`

  def apply(data: DataSet): DataSet = {
    val dataType = functionType.applies(data.dataType) match {
      case Left(error) => throw new RuntimeException(s"Types do not match ${error}")
      case Right(ok)   => ok
    }
    DataSet.natural(
      Source.OperationResult(Operation.Application(this, data)), dataType
    )
  }
}
