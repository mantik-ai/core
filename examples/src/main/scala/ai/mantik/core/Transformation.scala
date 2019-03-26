package ai.mantik.core

import ai.mantik.ds.funcational.FunctionType

/** Some A => B Transformation Algorithm */
case class Transformation(
    source: Source,
    functionType: FunctionType
) extends MantikItem {
  def apply(data: DataSet): DataSet = {
    val dataType = functionType.applies(data.dataType) match {
      case Left(error) => throw new RuntimeException(s"Types do not match ${error}")
      case Right(ok)   => ok
    }
    DataSet(Source.AppliedTransformation(data, this), dataType)
  }
}