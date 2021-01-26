package ai.mantik.ds.converter

import ai.mantik.ds.converter.casthelper.{ FundamentalCasts, ImageHelper, TensorHelper }
import ai.mantik.ds.element._
import ai.mantik.ds._
import cats.implicits._

/**
 * A Single Cast Operation, converting one element type to another one.
 * @param from source type
 * @param to destination type
 * @param loosing if true, the cast is loosing precision
 * @param canFail if true, the cast can fail
 * @param op the cast operation
 */
case class Cast(from: DataType, to: DataType, loosing: Boolean, canFail: Boolean, op: Element => Element) extends DataTypeConverter {

  /** Append another cast to this cast. */
  def append(other: Cast): Cast = {
    require(other.from == to)
    if (isIdentity) {
      // this is a identity cast
      return other
    }
    if (other.isIdentity) {
      // other is identity cast
      return this
    }
    Cast(
      from, other.to, loosing || other.loosing, canFail || other.canFail, x => other.op(op(x))
    )
  }

  /** Returns true if this cast is safe to execute */
  def isSafe: Boolean = !loosing && !canFail

  override def targetType: DataType = to

  def isIdentity: Boolean = from == to

  override def convert(element: Element): Element = op(element)
}

object Cast {

  /** Find a cast from one type to another. */
  def findCast(from: DataType, to: DataType): Either[String, Cast] = {
    (from, to) match {
      case (f: FundamentalType, t: FundamentalType) =>
        findFundamentalCast(f, t)
      case (f: FundamentalType, t: Tensor) =>
        findFundamentalToTensorCast(f, t)
      case (t: Tensor, f: FundamentalType) =>
        findTensorToFundamentalCast(t, f)
      case (t: Tensor, i: Image) =>
        findTensorToImageCast(t, i)
      case (i: Image, t: Tensor) =>
        findImageToTensorCast(i, t)
      case (from: Tensor, to: Tensor) =>
        findTensorToTensorCast(from, to)
      case (from: Image, to: Image) =>
        findImageToImageCast(from, to)
      case (from: Nullable, to: Nullable) if from.underlying == FundamentalType.VoidType =>
        Right(nullableVoidToAnyNullable(to))
      case (from: Nullable, to: Nullable) =>
        findNullableToNullableCast(from, to)
      case (from: Nullable, to) =>
        findNullableToNonNullableCast(from, to)
      case (from, to: Nullable) =>
        findNonNullableToNullableCast(from, to)
      case (from: ArrayT, to: ArrayT) =>
        findArrayCast(from, to)
      case (from: Struct, to: Struct) =>
        findStructCast(from, to)
      case (from: Struct, to: DataType) =>
        findUnpackingCast(from, to)
      case (from: DataType, to: Struct) =>
        findPackingCast(from, to)
      case _ =>
        Left(s"Cannot cast from ${from} to ${to}")
    }
  }

  /** Find a cast operation from fundamental to fundamental types. */
  def findFundamentalCast(from: FundamentalType, to: FundamentalType): Either[String, Cast] = {
    FundamentalCasts.findFundamentalCast(from, to)
  }

  private def findFundamentalToTensorCast(ft: FundamentalType, tensor: Tensor): Either[String, Cast] = {
    if (tensor.shape != List(1)) {
      return Left("Cannot cast a fundamental type into a non 1 Tensor")
    }

    for {
      toTensorType <- findFundamentalCast(ft, tensor.componentType)
      packer = TensorHelper.tensorPacker(tensor.componentType)
    } yield toTensorType.append(
      converter.Cast(
        tensor.componentType,
        tensor,
        loosing = false,
        canFail = false,
        op = e => packer(IndexedSeq(e.asInstanceOf[Primitive[_]])))
    )
  }

  private def findTensorToFundamentalCast(tensor: Tensor, ft: FundamentalType): Either[String, Cast] = {
    if (tensor.shape != List(1)) {
      return Left("Cannot cast a non-1 tensor to a fundamental type")
    }
    for {
      toSingleType <- findFundamentalCast(tensor.componentType, ft)
      unpacker = TensorHelper.tensorUnpacker(tensor.componentType)
    } yield {
      converter.Cast(
        tensor,
        tensor.componentType,
        loosing = false,
        canFail = false,
        op = e => unpacker(e.asInstanceOf[TensorElement[_]]).head
      ).append(toSingleType)
    }
  }

  private def findTensorToImageCast(tensor: Tensor, image: Image): Either[String, Cast] = {
    if (image.width * image.height != tensor.packedElementCount) {
      return Left("Cannot cast a tensor with different element count to a image")
    }
    val imageComponentType = image.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only create single component images")
    }
    for {
      imagePacker <- ImageHelper.imagePacker(image)
      typeConversion <- findFundamentalCast(tensor.componentType, imageComponentType)
      tensorUnpacker = TensorHelper.tensorUnpacker(tensor.componentType)
    } yield {
      converter.Cast(
        from = tensor,
        to = image,
        loosing = typeConversion.loosing,
        canFail = typeConversion.canFail,
        op = { e =>
          val elements = tensorUnpacker(e.asInstanceOf[TensorElement[_]])
          val casted = if (typeConversion.isIdentity) elements else {
            elements.map(x => typeConversion.op(x).asInstanceOf[Primitive[_]])
          }
          imagePacker(casted)
        }
      )
    }
  }

  private def findImageToTensorCast(image: Image, tensor: Tensor): Either[String, Cast] = {
    if (image.width * image.height != tensor.packedElementCount) {
      return Left("Cannot cast a tensor with different element count to a image")
    }
    val imageComponentType = image.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only cast single component images")
    }
    for {
      imageUnpacker <- ImageHelper.imageUnpacker(image)
      typeConversion <- findFundamentalCast(imageComponentType, tensor.componentType)
      tensorPacker = TensorHelper.tensorPacker(tensor.componentType)
    } yield {
      converter.Cast(
        from = image,
        to = tensor,
        loosing = typeConversion.loosing,
        canFail = typeConversion.canFail,
        op = { i =>
          val imageElements = imageUnpacker(i.asInstanceOf[ImageElement])
          val casted = if (typeConversion.isIdentity) imageElements else {
            imageElements.map(x => typeConversion.op(x).asInstanceOf[Primitive[_]])
          }
          tensorPacker(casted)
        }
      )
    }
  }

  private def findTensorToTensorCast(from: Tensor, to: Tensor): Either[String, Cast] = {
    if (from.packedElementCount != to.packedElementCount) {
      return Left(s"Cannot cast ${from} to ${to} due incompatible shape")
    }
    for {
      componentCast <- findFundamentalCast(from.componentType, to.componentType)
      unpacker = TensorHelper.tensorUnpacker(from.componentType)
      packer = TensorHelper.tensorPacker(to.componentType)
    } yield {
      converter.Cast(
        from = from,
        to = to,
        loosing = componentCast.loosing,
        canFail = componentCast.canFail,
        op = { e: Element =>
          if (componentCast.isIdentity) {
            // as we using flat serialization, we can go the very fast path, data is equal
            e
          } else {
            val unpacked = unpacker(e.asInstanceOf[TensorElement[_]])
            val converted = unpacked.map(x => componentCast.op(x).asInstanceOf[Primitive[_]])
            packer(converted)
          }
        }
      )
    }
  }

  private def findImageToImageCast(from: Image, to: Image): Either[String, Cast] = {
    val srcComponentType = from.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only cast single component images")
    }
    val dstComponentType = to.components.map(_._2.componentType) match {
      case List(single) => single
      case _            => return Left("Can only cast single component images")
    }
    if (from.width != to.width || from.height != to.height) {
      return Left("Can only cast images with same size. You can override this through a tensor cast")
    }
    for {
      componentCast <- findFundamentalCast(srcComponentType, dstComponentType)
      imageUnpacker <- ImageHelper.imageUnpacker(from)
      imagePacker <- ImageHelper.imagePacker(to)
    } yield {
      converter.Cast(
        from = from,
        to = to,
        loosing = componentCast.loosing,
        canFail = componentCast.canFail,
        op = { e =>
          val unpacked = imageUnpacker(e.asInstanceOf[ImageElement])
          val casted = unpacked.map(x => (componentCast.op(x).asInstanceOf[Primitive[_]]))
          imagePacker(casted)
        }
      )
    }
  }

  private def findNullableToNullableCast(from: Nullable, to: Nullable): Either[String, Cast] = {
    findCast(from.underlying, to.underlying).map { underlyingCast =>
      Cast(
        from = from,
        to = to,
        loosing = underlyingCast.loosing,
        canFail = underlyingCast.canFail,
        op = { e =>
          e.asInstanceOf[NullableElement] match {
            case NullElement    => NullElement
            case SomeElement(x) => SomeElement(underlyingCast.convert(x))
          }
        }
      )
    }
  }

  private def findNullableToNonNullableCast(from: Nullable, to: DataType): Either[String, Cast] = {
    findCast(from.underlying, to).map { underlyingCast =>
      Cast(
        from = from,
        to = to,
        loosing = underlyingCast.loosing,
        canFail = true,
        op = { e =>
          e.asInstanceOf[NullableElement] match {
            case NullElement    => throw new NoSuchElementException("Could not convert null to existing value")
            case SomeElement(x) => underlyingCast.convert(x)
          }
        }
      )
    }
  }

  private def findNonNullableToNullableCast(from: DataType, to: Nullable): Either[String, Cast] = {
    findCast(from, to.underlying).map { underlyingCast =>
      Cast(
        from = from,
        to = to,
        loosing = underlyingCast.loosing,
        canFail = underlyingCast.canFail,
        op = { e =>
          SomeElement(underlyingCast.convert(e))
        }
      )
    }
  }

  private def nullableVoidToAnyNullable(to: Nullable): Cast = {
    Cast(
      from = Nullable(FundamentalType.VoidType),
      to = to,
      loosing = true,
      canFail = false,
      op = { _ =>
        NullElement
      }
    )
  }

  private def findArrayCast(from: ArrayT, to: ArrayT): Either[String, Cast] = {
    findCast(from.underlying, to.underlying).map { underlyingCast =>
      Cast(
        from = from,
        to = to,
        loosing = underlyingCast.loosing,
        canFail = underlyingCast.canFail,
        op = { from =>
          ArrayElement(from.asInstanceOf[ArrayElement].elements.map(underlyingCast.op))
        }
      )
    }
  }

  private def findStructCast(from: Struct, to: Struct): Either[String, Cast] = {
    if (from.arity != to.arity) {
      Left(s"Can not cast from ${from}(arity=${from.arity}) to ${to}(arity=${to.arity})")
    } else {
      from.fields.values.toVector.zip(to.fields.values.toVector).map {
        case (fromDt, toDt) =>
          findCast(fromDt, toDt)
      }.sequence.map { casts =>
        Cast(
          from = from,
          to = to,
          canFail = casts.exists(_.canFail),
          loosing = casts.exists(_.loosing),
          op = { from =>
            StructElement(
              from.asInstanceOf[StructElement]
                .elements.zip(casts)
                .map {
                  case (e, cast) =>
                    cast.op(e)
                }
            )
          }
        )
      }
    }
  }

  private def findPackingCast(from: DataType, to: Struct): Either[String, Cast] = {
    if (to.arity != 1) {
      Left(s"Can only pack single-arity tuples")
    } else {
      val singleType = to.fields.values.head
      findCast(from, singleType).map { cast =>
        Cast(
          from = from,
          to = to,
          canFail = cast.canFail,
          loosing = cast.loosing,
          op = { from =>
            StructElement(cast.op(from))
          }
        )
      }
    }
  }

  private def findUnpackingCast(from: Struct, to: DataType): Either[String, Cast] = {
    if (from.arity != 1) {
      Left(s"Can only unpack single-arity tuples")
    } else {
      val singleType = from.fields.values.head
      findCast(singleType, to).map { cast =>
        Cast(
          from = from,
          to = to,
          canFail = cast.canFail,
          loosing = cast.loosing,
          op = { from =>
            cast.op(from.asInstanceOf[StructElement].elements.head)
          }
        )
      }
    }
  }
}
