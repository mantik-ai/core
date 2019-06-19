package ai.mantik.planner

import ai.mantik.ds.Errors.FeatureNotSupported
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.planner.select.Select
import ai.mantik.repository.{ AlgorithmDefinition, Mantikfile }

/** Some A => B Transformation Algorithm */
case class Algorithm(
    source: Source,
    private[planner] val mantikfile: Mantikfile[AlgorithmDefinition],
    private[planner] val select: Option[Select] = None,
) extends ApplicableMantikItem {

  override type DefinitionType = AlgorithmDefinition
  override type OwnType = Algorithm

  override def functionType: FunctionType = mantikfile.definition.`type`

  override protected def withMantikfile(mantikfile: Mantikfile[AlgorithmDefinition]): Algorithm = {
    copy(
      source = source.derive,
      mantikfile = mantikfile
    )
  }
}

object Algorithm {

  /** Convert a Select statement into an algorithm. */
  def fromSelect(select: Select): Algorithm = {
    val mantikFile = select.compileToSelectMantikfile() match {
      case Left(error) =>
        // TODO: Better exception
        throw new FeatureNotSupported(s"Could not compile select ${error}")
      case Right(ok) => ok
    }
    Algorithm(Source.constructed(), mantikFile, Some(select))
  }
}
