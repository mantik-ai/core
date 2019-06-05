package ai.mantik.planner

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.repository.{ AlgorithmDefinition, Mantikfile }

/** Some A => B Transformation Algorithm */
case class Algorithm(
    source: Source,
    private[planner] val mantikfile: Mantikfile[AlgorithmDefinition]
) extends MantikItem {

  override type DefinitionType = AlgorithmDefinition
  override type OwnType = Algorithm

  def functionType: FunctionType = mantikfile.definition.`type`

  def apply(data: DataSet): DataSet = {
    val adapted = data.autoAdaptOrFail(functionType.input)

    DataSet.natural(
      Source.OperationResult(Operation.Application(this, adapted)), functionType.output
    )
  }

  override protected def withMantikfile(mantikfile: Mantikfile[AlgorithmDefinition]): Algorithm = {
    copy(
      mantikfile = mantikfile
    )
  }
}
