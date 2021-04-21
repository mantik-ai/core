package ai.mantik.ds.funcational

import ai.mantik.ds.{DataType, funcational}
import ai.mantik.ds.helper.circe.{CirceJson, DiscriminatorDependentCodec}

case class FunctionType(
    input: DataType,
    output: DataType
)

object FunctionType {
  implicit val codec = CirceJson.makeSimpleCodec[FunctionType]
}
