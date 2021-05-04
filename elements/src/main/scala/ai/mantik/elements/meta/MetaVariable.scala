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
package ai.mantik.elements.meta

import ai.mantik.ds.element.{SingleElementBundle, TabularBundle}
import ai.mantik.ds.formats.json.JsonFormat
import io.circe.Decoder.Result
import io.circe.{Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}

/**
  * A Meta variable used in [[MetaVariableApplication]].
  * @param name name of the meta variable
  * @param value value of the meta variable.
  * @param fix if fix, a meta variable may not be changed anymore.
  */
case class MetaVariable(
    name: String,
    value: SingleElementBundle,
    fix: Boolean = false
) {

  /** The json encoded value. */
  lazy val jsonValue: Json = JsonFormat.serializeBundleValue(value)
}

object MetaVariable {

  implicit val encoder: Encoder[MetaVariable] = new Encoder[MetaVariable] {
    override def apply(a: MetaVariable): Json = {
      Json
        .obj(
          "name" -> Json.fromString(a.name),
          "fix" -> Json.fromBoolean(a.fix)
        )
        .deepMerge(
          Json.fromJsonObject(JsonFormat.encodeObject(a.value))
        )
    }
  }

  implicit val decoder: Decoder[MetaVariable] = new Decoder[MetaVariable] {
    override def apply(c: HCursor): Result[MetaVariable] = {
      for {
        value <- JsonFormat.decodeJson(c.value)
        singleValue <- value match {
          case s: SingleElementBundle => Right(s)
          case m: TabularBundle       => Left(DecodingFailure("Only single element bundles supported", Nil))
        }
        name <- c.get[String]("name")
        fix <- c.getOrElse[Boolean]("fix")(false)
      } yield {
        MetaVariable(
          name,
          singleValue,
          fix
        )
      }
    }
  }
}
