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
package ai.mantik.bridge.scalafn

import ai.mantik.elements
import ai.mantik.elements.meta.MetaJson
import ai.mantik.elements._
import io.circe.{Decoder, DecodingFailure, Json, ObjectEncoder}
import io.circe.syntax._

/** Header for ScalaFn-Bridge */
case class ScalaFnHeader(
    combiner: CombinerDefinition,
    fnType: ScalaFnType
) {

  def toMantikHeader: MantikHeader[CombinerDefinition] = {
    val defJson = ScalaFnHeader.encoder.encodeObject(this)
    MantikHeader(
      combiner,
      MetaJson.withoutMetaVariables(defJson),
      MantikHeaderMeta()
    )
  }
}

object ScalaFnHeader {
  val bridgeName = NamedMantikId("builtin/scalafn")

  implicit val encoder: ObjectEncoder[ScalaFnHeader] = ObjectEncoder { instance =>
    (instance.combiner: elements.MantikDefinition).asJsonObject.+:(
      "fnType" -> instance.fnType.asJson
    )
  }

  implicit val decoder: Decoder[ScalaFnHeader] = Decoder { json =>
    for {
      mantikDefinition <- json.as[MantikDefinition]
      casted <- mantikDefinition match {
        case c: CombinerDefinition => Right(c)
        case _                     => Left(DecodingFailure(s"Expected Combiner definition, got ${mantikDefinition.kind}", json.history))
      }
      t <- json.downField("fnType").as[ScalaFnType]
    } yield {
      ScalaFnHeader(casted, t)
    }
  }
}
