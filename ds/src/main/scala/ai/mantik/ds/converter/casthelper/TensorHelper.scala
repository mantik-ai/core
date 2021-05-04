/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.ds.converter.casthelper

import ai.mantik.ds.element.{Primitive, PrimitiveEncoder, TensorElement}
import ai.mantik.ds.{FundamentalType, Tensor}

/** Packs and unpacks Tensors. */
private[ds] object TensorHelper {

  def tensorUnpacker(ft: FundamentalType): TensorElement[_] => IndexedSeq[Primitive[_]] = {
    tensorPrimitiveConverters(ft).unpacker
  }

  def tensorPacker(ft: FundamentalType): IndexedSeq[Primitive[_]] => TensorElement[_] = {
    tensorPrimitiveConverters(ft).packer
  }

  private case class TensorPrimitiveConverter(
      ft: FundamentalType,
      packer: IndexedSeq[Primitive[_]] => TensorElement[_],
      unpacker: TensorElement[_] => IndexedSeq[Primitive[_]]
  )

  private def makeTensorPrimitiveConverter[FT <: FundamentalType, ST](
      ft: FT
  )(implicit aux: PrimitiveEncoder.Aux[FT, ST]): TensorPrimitiveConverter =
    TensorPrimitiveConverter(
      ft,
      p => TensorElement(p.map(aux.unwrap)),
      t => t.elements.map(x => aux.wrap(x.asInstanceOf[ST]))
    )

  private lazy val tensorPrimitiveConverters: Map[FundamentalType, TensorPrimitiveConverter] = Seq(
    makeTensorPrimitiveConverter(FundamentalType.Uint8),
    makeTensorPrimitiveConverter(FundamentalType.Int8),
    makeTensorPrimitiveConverter(FundamentalType.Uint32),
    makeTensorPrimitiveConverter(FundamentalType.Int32),
    makeTensorPrimitiveConverter(FundamentalType.Uint64),
    makeTensorPrimitiveConverter(FundamentalType.Int64),
    makeTensorPrimitiveConverter(FundamentalType.Float32),
    makeTensorPrimitiveConverter(FundamentalType.Float64),
    makeTensorPrimitiveConverter(FundamentalType.BoolType),
    makeTensorPrimitiveConverter(FundamentalType.VoidType),
    makeTensorPrimitiveConverter(FundamentalType.StringType)
  ).map(x => x.ft -> x).toMap
}
