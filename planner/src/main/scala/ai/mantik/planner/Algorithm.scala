package ai.mantik.planner

import java.util.UUID

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.elements.{ AlgorithmDefinition, MantikHeader }
import ai.mantik.planner.repository.Bridge
import ai.mantik.planner.select.Select

/** Some A => B Transformation Algorithm */
case class Algorithm(
    core: MantikItemCore[AlgorithmDefinition],
    private[planner] val select: Option[Select] = None
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
    Algorithm(MantikItemCore(source, mantikHeader, bridge = Some(bridge)))
  }

  /** Convert a Select statement into an algorithm. */
  def fromSelect(select: Select): Algorithm = {
    val mantikHeader = select.compileToSelectMantikHeader() match {
      case Left(error) =>
        // TODO: Better exception
        throw new FeatureNotSupported(s"Could not compile select ${error}")
      case Right(ok) => ok
    }
    Algorithm(
      MantikItemCore(
        Source.constructed(),
        mantikHeader,
        bridge = Some(Bridge.selectBridge)
      ), Some(select))
  }
}
