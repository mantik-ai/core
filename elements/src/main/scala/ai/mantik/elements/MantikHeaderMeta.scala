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
package ai.mantik.elements

import ai.mantik.ds.helper.circe.CirceJson
import io.circe.{Decoder, ObjectEncoder}

/**
  * Contains Meta information inside a [[MantikHeader]].
  * All fields are optional.
  *
  * All elements are directly parsed from the JSON.
  *
  * @param author author of the file, for informative use only
  * @param authorEmail email of Author
  * @param name default name of the Artifact behind the MantikHeader.
  * @param version default version of the Artifact behind the mantik header.
  * @param account default account name of the Artifact behind the mantik header.
  */
case class MantikHeaderMeta(
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    account: Option[String] = None
) {

  /** Returns a MantikId for this Item, when a name is given. */
  def id: Option[NamedMantikId] = name.map { name =>
    NamedMantikId(
      name = name,
      version = version.getOrElse(NamedMantikId.DefaultVersion),
      account = account.getOrElse(NamedMantikId.DefaultAccount)
    )
  }

  /** Overrides all name related fields from an id. (name, version, account) */
  def withId(id: NamedMantikId): MantikHeaderMeta = {
    copy(
      name = Some(id.name),
      version = if (id.version == NamedMantikId.DefaultVersion) {
        None
      } else {
        Some(id.version)
      },
      account = if (id.account == NamedMantikId.DefaultAccount) {
        None
      } else {
        Some(id.account)
      }
    )
  }
}

object MantikHeaderMeta {
  implicit val codec: ObjectEncoder[MantikHeaderMeta] with Decoder[MantikHeaderMeta] =
    CirceJson.makeSimpleCodec[MantikHeaderMeta]
}
