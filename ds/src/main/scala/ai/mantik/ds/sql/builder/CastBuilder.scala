package ai.mantik.ds.sql.builder

import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.{ CastExpression, Expression }
import ai.mantik.ds.{ DataType, FundamentalType, Image, ImageChannel, ImageFormat, Nullable, Tensor }
import ai.mantik.ds.sql.parser.AST

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

  private def findCast(input: Expression, destinationType: AST.TypeNode): Either[String, DataType] = {
    destinationType match {
      case AST.NullableTypeNode(underlying) =>
        findCast(input, underlying).map(Nullable(_))
      case AST.FundamentalTypeNode(dataType: DataType) =>
        // TODO: Check if we support this cast
        Right(dataType)
      case AST.TensorTypeNode(underlying) =>
        // Only supported from plain images with one component and fundamental types
        buildToTensorCast(input.dataType, underlying)
      case AST.ImageTypeNode(underlying, channel) =>
        buildToImageCast(input.dataType, underlying, channel)
    }
  }

  private def buildToTensorCast(from: DataType, maybeUnderlying: Option[FundamentalType]): Either[String, DataType] = {
    from match {
      case f: FundamentalType =>
        // plain wrapping
        val underlying = maybeUnderlying.getOrElse(f)
        Right(Tensor(underlying, List(1)))
      case image: Image =>
        image.format match {
          case ImageFormat.Plain =>
            if (image.components.size == 1) {
              val singleComponent = image.components.head._2.componentType
              val underlying = maybeUnderlying.getOrElse(singleComponent)
              Right(Tensor(
                underlying,
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

  private def buildToImageCast(from: DataType, maybeUnderlying: Option[FundamentalType], maybeChannel: Option[ImageChannel]): Either[String, DataType] = {
    val channel = maybeChannel.getOrElse(ImageChannel.Black)
    from match {
      case f: FundamentalType =>
        // plain wrapping
        val underlying = maybeUnderlying.getOrElse(f)
        Right(Image.plain(1, 1, channel -> underlying))
      case tensor: Tensor =>
        tensor.shape match {
          case List(height, width) =>
            val singleComponent = tensor.componentType
            val underlying = maybeUnderlying.getOrElse(singleComponent)
            Right(
              Image.plain(width, height, channel -> underlying)
            )
          case other =>
            Left(s"Cannot convert a tensor of shape ${other} to an image")
        }
      case other =>
        Left(s"Unsupported cast from ${other} to tensor")
    }
  }
}
