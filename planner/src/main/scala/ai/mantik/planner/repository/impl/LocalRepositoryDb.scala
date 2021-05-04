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
package ai.mantik.planner.repository.impl

import java.nio.file.Path
import java.util.UUID

import ai.mantik.planner.utils.sqlite.QuillSqlite

/**
  * Contains the Database Adapter for the local Repository.
  */
private[impl] class LocalRepositoryDb(dbFile: Path) {
  import LocalRepositoryDb._

  val quill = new QuillSqlite(dbFile)

  // Ensuring Database schema
  new MantikDbEvolutions(quill).ensureCurrentVersion()

  import quill.context._

  val names = quote {
    querySchema[DbMantikName](
      "mantik_name",
      _.id -> "id",
      _.account -> "account",
      _.name -> "name",
      _.version -> "version",
      _.currentItemId -> "current_item_id"
    )
  }

  val deployments = quote {
    querySchema[DbDeploymentInfo](
      ("mantik_deployment_info"),
      _.itemId -> "item_id",
      _.name -> "name",
      _.internalUrl -> "internal_url",
      _.externalUrl -> "external_url",
      _.timestamp -> "timestamp"
    )
  }

  val subDeployments = quote {
    querySchema[DbSubDeploymentInfo](
      "mantik_sub_deployment_info",
      _.itemId -> "item_id",
      _.subId -> "sub_id",
      _.name -> "name",
      _.internalUrl -> "internal_url"
    )
  }

  /** Quill Query Schema for accessing artifacts. */
  val items = quote {
    querySchema[DbMantikItem](
      "mantik_item",
      _.itemId -> "item_id",
      _.mantikheader -> "mantikheader",
      _.fileId -> "file_id",
      _.kind -> "kind",
      _.executorStorageId -> "executor_storage_id"
    )
  }

  /** Shutdown DB Access. */
  def shutdown(): Unit = {
    quill.shutdown()
  }
}

private[impl] object LocalRepositoryDb {

  case class DbMantikName(
      id: UUID = UUID.randomUUID(),
      account: String,
      name: String,
      version: String,
      currentItemId: String
  )

  // Item stored in the database
  case class DbMantikItem(
      itemId: String,
      mantikheader: String,
      fileId: Option[String],
      kind: String,
      executorStorageId: Option[String]
  )

  case class DbDeploymentInfo(
      itemId: String,
      name: String,
      internalUrl: String,
      externalUrl: Option[String],
      timestamp: java.util.Date
  )

  case class DbSubDeploymentInfo(
      itemId: String,
      subId: String,
      name: String,
      internalUrl: String
  )
}
