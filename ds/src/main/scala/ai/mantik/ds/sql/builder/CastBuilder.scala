package ai.mantik.ds.sql.builder

import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.SingleElementBundle
import ai.mantik.ds.operations.BinaryOperation
import ai.mantik.ds.sql.{CastExpression, ConstantExpression, Expression}
import ai.mantik.ds.{ArrayT, DataType, FundamentalType, Image, ImageChannel, ImageFormat, Nullable, Struct, Tensor}
import ai.mantik.ds.sql.parser.AST

import scala.util.control.NonFatal

/** Converts Casts and other type related stuff */
private[sql] object CastBuilder {

  /** Returns the data typ,e which ban be used for comparing left and right. */
  def comparisonType(left: Expression, right: Expression): Either[String, DataType] = {
    comparisonType(left.dataType, right.dataType).left.map { error =>
      s"Error finding comparison type for ${left}/${right} ${error}"
    }
  }

  def comparisonType(left: DataType, right: DataType): Either[String, DataType] = {
    if (left == right) {
      Right(left)
    } else {
      (left, right) match {
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
        case (a: FundamentalType.FloatingPoint, b: FundamentalType.IntegerType) if a.fraction >= b.bits =>
          Right(a)
        case (FundamentalType.Float32, b: FundamentalType.IntegerType) if FundamentalType.Float64.fraction >= b.bits =>
          Right(FundamentalType.Float64)
        case (a: FundamentalType.IntegerType, b: FundamentalType.FloatingPoint) =>
          comparisonType(b, a)
        case (a: Nullable, b: Nullable) =>
          comparisonType(a.underlying, b.underlying).map(Nullable.makeNullable)
        case (a: Nullable, b) =>
          comparisonType(a.underlying, b).map(Nullable.makeNullable)
        case (a, b: Nullable) =>
          comparisonType(a, b.underlying).map(Nullable.makeNullable)
        case (a, b) =>
          Left(s"Could not unify ${left} with ${right}")
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
      Cast.findCast(in.dataType, expectedType).flatMap { cast =>
        in match {
          case c: ConstantExpression =>
            // directly execute the cast
            try {
              val casted = cast.convert(c.value.element)
              Right(ConstantExpression(SingleElementBundle(cast.to, casted)))
            } catch {
              case NonFatal(e) =>
                Left(s"Cast from ${in.dataType} to ${expectedType} for constant failed ${e}")
            }
          case otherwise =>
            Right(CastExpression(otherwise, expectedType))
        }
      }
    }
  }

  /** Like [[wrapType]], but also accepts a nullable expression */
  def wrapTypeWithNullableSupport(in: Expression, dataType: DataType): Either[String, Expression] = {
    in.dataType match {
      case Nullable(_) =>
        wrapType(in, Nullable(dataType))
      case _ =>
        wrapType(in, dataType)
    }
  }

  def ensureArray(in: Expression, maybeNullable: Boolean): Either[String, (Expression, ArrayT)] = {
    in.dataType match {
      case a: ArrayT                            => Right(in -> a)
      case Nullable(a: ArrayT) if maybeNullable => Right(in -> a)
      case somethingElse                        => Left(s"Cannot ensure ${somethingElse} as array")
    }
  }

  def ensureStruct(in: Expression, maybeNullable: Boolean): Either[String, (Expression, Struct)] = {
    in.dataType match {
      case s: Struct                            => Right(in -> s)
      case Nullable(s: Struct) if maybeNullable => Right(in -> s)
      case somethingElse                        => Left(s"Cannot ensure ${somethingElse} is struct")
    }
  }

  def buildCast(expression: Expression, castNode: AST.CastNode): Either[String, Expression] = {
    for {
      dataType <- convertTypeNode(expression, castNode.destinationType)
    } yield CastExpression(expression, dataType)
  }

  private def convertTypeNode(input: Expression, destinationType: AST.TypeNode): Either[String, DataType] = {
    destinationType match {
      case AST.NullableTypeNode(underlying) =>
        convertTypeNode(input, underlying).map(Nullable(_))
      case AST.ArrayTypeNode(underlying) =>
        convertTypeNode(input, underlying).map(ArrayT(_))
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
              Right(
                Tensor(
                  underlying,
                  List(image.height, image.width)
                )
              )
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

  private def buildToImageCast(
      from: DataType,
      maybeUnderlying: Option[FundamentalType],
      maybeChannel: Option[ImageChannel]
  ): Either[String, DataType] = {
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
