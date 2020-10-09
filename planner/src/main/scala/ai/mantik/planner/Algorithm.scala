package ai.mantik.planner

import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, MantikHeader }
import ai.mantik.planner.repository.Bridge

/** Some A => B Transformation Algorithm */
case class Algorithm(
    core: MantikItemCore[AlgorithmDefinition]
) extends ApplicableMantikItem with BridgedMantikItem {

  override type DefinitionType = AlgorithmDefinition
  override type OwnType = Algorithm

  override def functionType: FunctionType = mantikHeader.definition.`type`

  override protected def withCore(core: MantikItemCore[AlgorithmDefinition]): Algorithm = {
    copy(core = core)
  }
}

object Algorithm {

  def apply(source: Source, mantikHeader: MantikHeader[AlgorithmDefinition], bridge: Bridge): Algorithm = {
    Algorithm(MantikItemCore(source, mantikHeader, bridge))
  }
}
