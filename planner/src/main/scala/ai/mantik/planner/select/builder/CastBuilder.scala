package ai.mantik.planner.select.builder

import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.{ DataType, FundamentalType, Image, ImageFormat, Tensor }
import ai.mantik.planner.select.{ CastExpression, Expression }
import ai.mantik.planner.select.parser.AST

/** Converts Casts and other type related stuff */
private[builder] object CastBuilder {

  /** Returns the data typ,e which ban be used for comparing left and right. */
  def comparisonType(left: Expression, right: Expression): Either[String, DataType] = {
    if (left.dataType == right.dataType) {
      Right(left.dataType)
    } else {
      (left.dataType, right.dataType) match {
        case (a: FundamentalType.IntegerType, b: FundamentalType.IntegerType) =>
          if (a.bits > b.bits) {
            Right(a)
          } else {
            Right(b)
          }
        case (a: FundamentalType.FloatingPoint, b: FundamentalType.FloatingPoint) =>
          if (a.bits > b.bits) {
            Right(a)
          } else {
            Right(b)
          }
        case (a, b) =>
          Left(s"Could not unify data type of ${left}:${a} and ${right}:${b}")
      }
    }
  }

  /** Returns the type which can be used for doing an operation on both types. */
  def operationType(op: BinaryOperation, left: Expression, right: Expression): Either[String, DataType] = {
    // currently the same
    comparisonType(left, right)
  }

  /** Wraps a type into an expected type. */
  def wrapType(in: Expression, expectedType: DataType): Either[String, Expression] = {
    if (in.dataType == expectedType) {
      Right(in)
    } else {
      // assumming that all is good
      // TODO: Should have further checking
      Right(CastExpression(in, expectedType))
    }
  }

  def buildCast(expression: Expression, castNode: AST.CastNode): Either[String, Expression] = {
    for {
      dataType <- findCast(expression, castNode.destinationType)
    } yield CastExpression(expression, dataType)
  }

  private def findCast(input: Expression, castNode: AST.TypeNode): Either[String, DataType] = {
    castNode match {
      case AST.FundamentalTypeNode(dataType: DataType) =>
        // TODO: Check if we support this cast
        Right(dataType)
      case AST.TensorTypeNode =>
        // Only supported from plain images with one component and fundamental types
        buildTensorCast(input.dataType)
    }
  }

  private def buildTensorCast(from: DataType): Either[String, DataType] = {
    from match {
      case f: FundamentalType =>
        // plain wrapping
        Right(Tensor(f, List(1)))
      case image: Image =>
        image.format match {
          case ImageFormat.Plain =>
            if (image.components.size == 1) {
              val singleComponent = image.components.head._2
              Right(Tensor(
                singleComponent.componentType,
                List(image.height, image.width)
              ))
            } else {
              Left(s"No cast from ${image} to tensor supported with multiple columns")
            }
          case other =>
            Left(s"No cast from ${image} to tensor supported")
        }
      case other =>
        Left(s"Unsupported cast from ${other} to tensor")
    }
  }
}
