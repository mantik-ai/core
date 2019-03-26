package ai.mantik.ds.funcational

import ai.mantik.ds.{ DataType, funcational }
import ai.mantik.ds.helper.circe.{ CirceJson, DiscriminatorDependentCodec }

/** A Type of a function. */
trait FunctionType {

  /**
   * Returns the output type of given input type.
   * @return Left with error if not applicable.
   */
  def applies(input: DataType): Either[String, DataType]
}

object FunctionType extends DiscriminatorDependentCodec[FunctionType] {
  override val subTypes = Seq(
    makeSubType[SimpleFunction]("simple", true)
  )
}

/** A Function from one type to another type. */
case class SimpleFunction(
    input: DataType,
    output: DataType
) extends FunctionType {

  override def applies(input: DataType): Either[String, DataType] = {
    if (this.input == input) {
      Right(output)
    } else {
      Left(s"Input ${input} is not expected ${this.input}")
    }
  }
}