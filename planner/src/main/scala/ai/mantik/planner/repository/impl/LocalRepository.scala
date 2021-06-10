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

import java.io.File
import java.nio.file.{Files, Path}
import java.sql.Timestamp

import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.elements.errors.{ErrorCodes, MantikException}
import ai.mantik.elements.{ItemId, MantikHeader, MantikId, NamedMantikId}
import ai.mantik.planner.repository.impl.LocalRepository.DirectoryConfigKey
import ai.mantik.planner.repository.{DeploymentInfo, MantikArtifact, Repository, SubDeploymentInfo}
import ai.mantik.planner.repository.impl.LocalRepositoryDb._

import scala.concurrent.{ExecutionContext, Future}
import io.circe.parser
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import org.sqlite.{SQLiteErrorCode, SQLiteException}

import scala.util.{Failure, Success}

/** A local repository for artifacts based upon Sqlite. */
@Singleton
class LocalRepository(val directory: Path)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with Repository {

  @Inject
  def this()(implicit akkaRuntime: AkkaRuntime) {
    this(
      new File(
        akkaRuntime.config.getString(LocalRepository.DirectoryConfigKey)
      ).toPath
    )
  }

  // Note: in general we are using the plain ExecutionContext here
  // Which should be ok for local usage (but not servers).
  // On servers it's not advisable to use Sqlite anyway.

  val dbFile = directory.resolve("items.db")
  logger.info(s"Initializing in ${dbFile}")

  val db = new LocalRepositoryDb(dbFile)

  import db.quill.context._

  case class DbArtifact(
      name: Option[DbMantikName],
      item: DbMantikItem,
      depl: Option[DbDeploymentInfo],
      subd: Vector[DbSubDeploymentInfo]
  )

  override def get(id: MantikId): Future[MantikArtifact] = {
    dbOperation(s"get $id") {
      val maybeItem = id match {
        case i: ItemId        => getByItemId(i)
        case n: NamedMantikId => getByName(n)
      }

      maybeItem match {
        case None => ErrorCodes.MantikItemNotFound.throwIt(s"Artifact with id ${id} not found")
        case Some(item) =>
          decodeDbArtifact(item)
      }
    }
  }

  private def getByName(id: NamedMantikId): Option[DbArtifact] = {
    run {
      for {
        name <- db.names.filter { n =>
          n.account == lift(id.account) && n.name == lift(id.name) && n.version == lift(id.version)
        }
        item <- db.items.join(_.itemId == name.currentItemId)
        depl <- db.deployments.leftJoin(_.itemId == item.itemId)
      } yield (name, item, depl)
    }.headOption.map { case (name, item, depl) =>
      val subDeployments = getSubDeployments(item.itemId)
      DbArtifact(Some(name), item, depl, subDeployments)
    }
  }

  private def getByItemId(id: ItemId): Option[DbArtifact] = {
    run {
      for {
        item <- db.items.filter(_.itemId == lift(id.toString))
        depl <- db.deployments.leftJoin(_.itemId == item.itemId)
      } yield (item, depl)
    }.headOption.map { case (item, depl) =>
      val subDeployments = getSubDeployments(item.itemId)
      DbArtifact(None, item, depl, subDeployments)
    }
  }

  private def getSubDeployments(itemId: String): Vector[DbSubDeploymentInfo] = {
    run {
      db.subDeployments.filter(_.itemId == lift(itemId))
    }.toVector
  }

  override def store(mantikArtifact: MantikArtifact): Future[Unit] = {
    dbOperation(s"store ${mantikArtifact.namedId.getOrElse("")} ${mantikArtifact.itemId}") {
      val converted = encodeDbArtifact(mantikArtifact)
      val namedId = mantikArtifact.namedId

      // This could be improved by using an upsert.
      transaction {

        // Let it crash if already existant.
        try {
          run(
            db.items.insert(lift(converted))
          )
        } catch {
          case s: SQLiteException
              if s.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY ||
                s.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT // newer version of sqlite throws this
              =>
            ErrorCodes.MantikItemConflict.throwIt("Items may not be overwritten with the same itemId")
        }

        namedId.foreach { mantikId =>
          tagItemExec(mantikArtifact.itemId, mantikId)
        }

        updateDeploymentStateOp(mantikArtifact.itemId, mantikArtifact.deploymentInfo)
      }
    }
  }

  /**
    * Forces mantikId to point to itemId.
    * @return true if something changed.
    */
  private def tagItemExec(itemId: ItemId, mantikId: NamedMantikId): Boolean = {
    val nameElement = DbMantikName(
      account = mantikId.account,
      name = mantikId.name,
      version = mantikId.version,
      currentItemId = itemId.toString
    )

    val existingItem = run {
      db.items.filter(_.itemId == lift(itemId.toString)).nonEmpty
    }

    // The database would also tell us, but DB Exceptions are hard to debug.
    if (!existingItem) {
      ErrorCodes.MantikItemNotFound.throwIt(s"Item ${itemId} doesn't exist")
    }

    val exists = run {
      db.names
        .filter { e =>
          e.account == lift(mantikId.account) &&
          e.name == lift(mantikId.name) &&
          e.version == lift(mantikId.version)
        }
        .map(x => (x.id, x.currentItemId))
    }.headOption

    exists match {
      case Some((_, itemId)) if itemId == nameElement.currentItemId =>
        // No change
        false
      case Some((id, _)) =>
        // Current Item points to another item
        // drop it and rewrite it
        run(db.names.filter(_.id == lift(id)).delete)
        run(db.names.insert(lift(nameElement)))
        true
      case None =>
        run {
          db.names.insert(lift(nameElement))
        }
        true
    }
  }

  override def ensureMantikId(id: ItemId, mantikId: NamedMantikId): Future[Boolean] = {
    dbOperation(s"ensureMantikId ${id} ${mantikId}") {
      transaction {
        tagItemExec(id, mantikId)
      }
    }
  }

  override def setDeploymentInfo(itemId: ItemId, info: Option[DeploymentInfo]): Future[Boolean] = {
    dbOperation(s"setDeploymentInfo ${itemId}") {
      transaction(updateDeploymentStateOp(itemId, info))
    }
  }

  private def updateDeploymentStateOp(itemId: ItemId, maybeDeployed: Option[DeploymentInfo]): Boolean = {
    maybeDeployed match {
      case None =>
        val deleted = run {
          db.deployments.filter(_.itemId == lift(itemId.toString)).delete
        }
        run {
          db.subDeployments.filter(_.itemId == lift(itemId.toString)).delete
        }
        deleted > 0
      case Some(info) =>
        val itemIdString = itemId.toString
        val converted = DbDeploymentInfo(
          itemId = itemIdString,
          name = info.name,
          internalUrl = info.internalUrl,
          externalUrl = info.externalUrl,
          timestamp = new Timestamp(info.timestamp.toEpochMilli)
        )

        // upsert hasn't worked (error [SQLITE_ERROR] SQL error or missing database (near "AS": syntax error) )

        val updateTrial = run(db.deployments.filter(_.itemId == lift(itemIdString)).update(lift(converted))) > 0
        val updateResponse = if (updateTrial) {
          // ok
          updateTrial
        } else {
          try {
            run(db.deployments.insert(lift(converted))) > 0
          } catch {
            case e: SQLiteException
                if e.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_FOREIGNKEY ||
                  e.getResultCode == SQLiteErrorCode.SQLITE_CONSTRAINT =>
              // entry not present.
              false
          }

        }

        val convertedSubDeployments = info.sub.map { case (key, value) =>
          DbSubDeploymentInfo(
            itemId = itemIdString,
            subId = key,
            name = value.name,
            internalUrl = value.internalUrl
          )
        }

        run(db.subDeployments.filter(_.itemId == lift(itemIdString)).delete)
        run {
          liftQuery(convertedSubDeployments).foreach { s =>
            db.subDeployments.insert(s)
          }
        }

        updateResponse
    }
  }

  override def remove(id: MantikId): Future[Boolean] = {
    dbOperation(s"remove ${id}") {
      id match {
        case named: NamedMantikId =>
          // note: the item itself is not deleted, but dereferenced.
          // it can still be referenced by other things (e.g. deployments)
          run {
            db.names.filter { n =>
              n.account == lift(named.account) && n.name == lift(named.name) && n.version == lift(named.version)
            }.delete
          } > 0
        case i: ItemId =>
          // note: this will fail, if it's still referenced by names / deployments
          transaction {
            val existingNames = run {
              db.names.filter(_.currentItemId == lift(i.toString))
            }
            if (existingNames.nonEmpty) {
              ErrorCodes.MantikItemConflict.throwIt("There are existing names for this item")
            }
            val existingDeployments = run {
              db.deployments.filter(_.itemId == lift(i.toString))
            }
            val existingSubDeployments = run {
              db.subDeployments.filter(_.itemId == lift(i.toString))
            }
            if (existingDeployments.nonEmpty || existingSubDeployments.nonEmpty) {
              ErrorCodes.MantikItemConflict.throwIt("There are existing deployments for this item")
            }
            run {
              db.items.filter { n =>
                n.itemId == lift(i.toString)
              }.delete
            } > 0
          }
      }
    }
  }

  addShutdownHook {
    db.shutdown()
    Future.successful(())
  }

  /**
    * Convert a DB Artifact to a artifact
    */
  private def decodeDbArtifact(dbArtifact: DbArtifact): MantikArtifact = {
    MantikArtifact(
      mantikHeader = dbArtifact.item.mantikheader,
      namedId = dbArtifact.name.map { name =>
        NamedMantikId(
          account = name.account,
          name = name.name,
          version = name.version
        )
      },
      fileId = dbArtifact.item.fileId,
      itemId = ItemId.fromString(dbArtifact.item.itemId),
      executorStorageId = dbArtifact.item.executorStorageId,
      deploymentInfo = dbArtifact.depl.map { depl =>
        DeploymentInfo(
          name = depl.name,
          internalUrl = depl.internalUrl,
          externalUrl = depl.externalUrl,
          timestamp = depl.timestamp.toInstant,
          sub = dbArtifact.subd.map { dbSubDeployment =>
            dbSubDeployment.subId -> SubDeploymentInfo(
              name = dbSubDeployment.name,
              internalUrl = dbSubDeployment.internalUrl
            )
          }.toMap
        )
      }
    )
  }

  /**
    * Encode a DB Artefact.
    * Each Db Artefacts have different Ids, so it can come to collisions.
    */
  private def encodeDbArtifact(a: MantikArtifact): DbMantikItem = {
    DbMantikItem(
      mantikheader = a.mantikHeader,
      fileId = a.fileId,
      itemId = a.itemId.toString,
      kind = a.parsedMantikHeader.definition.kind,
      executorStorageId = a.executorStorageId
    )
  }

  /** Run a DB Operation, looks for unhandled errors */
  private def dbOperation[T](name: => String)(f: => T): Future[T] = {
    Future(f).andThen {
      case Success(_) =>
        logger.debug(s"Executed ${name}")
      case Failure(e: MantikException) => // ok, handled
      case Failure(e) =>
        logger.error(s"Unhandled database error in $name", e)
    }
  }

  override def list(
      alsoAnonymous: Boolean,
      deployedOnly: Boolean,
      kindFilter: Option[String]
  ): Future[IndexedSeq[MantikArtifact]] = {
    dbOperation(s"list alsoAnonymous=${alsoAnonymous} deployedOnly=${deployedOnly} kindFilter=${kindFilter}") {
      // The filtering can't be easily moved to a later stage so it's directly inside the join expression
      // see https://github.com/getquill/quill/issues/1012

      val items = kindFilter match {
        case Some(kind) => quote { db.items.filter(_.kind == lift(kind)) }
        case None       => quote { db.items }
      }

      val namedItems = if (alsoAnonymous) {
        quote { items.leftJoin(db.names).on(_.itemId == _.currentItemId) }
      } else {
        quote { items.join(db.names).on(_.itemId == _.currentItemId).map { case (i, n) => (i, Some(n)) } }
      }

      val artifacts = run {
        for {
          (item, name) <- namedItems
          depl <- db.deployments.leftJoin(_.itemId == item.itemId)
          // if !deployedOnly || depl.isDefined
        } yield (item, name, depl)
      }.collect {
        // TODO: This filtering should be done on database side
        // but getquill makes it really complicated to push that down without writing multiple database calls
        case (item, name, depl) if !deployedOnly || depl.isDefined =>
          val subDeployments = getSubDeployments(item.itemId)
          decodeDbArtifact(DbArtifact(name, item, depl, subDeployments))
      }

      artifacts.toIndexedSeq
    }

  }

  override def byFileId(fileId: String): Future[Seq[MantikArtifact]] = {
    dbOperation(s"byFileId fileId=${fileId}") {
      val query = quote {
        db.items
          .filter(_.fileId == lift(Some(fileId): Option[String]))
          .leftJoin(db.names)
          .on(_.itemId == _.currentItemId)
          .leftJoin(db.deployments)
          .on(_._1.itemId == _.itemId)
      }
      val artifacts = run(query).map { case ((item, name), depl) =>
        (item, name, depl)
      }
      artifacts.map { case (item, name, depl) =>
        val subDeployments = getSubDeployments(item.itemId)
        decodeDbArtifact(DbArtifact(name, item, depl, subDeployments))
      }
    }
  }

  override def updateExecutorStorageId(itemId: ItemId, executorStorageId: Option[String]): Future[Boolean] = {
    dbOperation(s"updateRemoteFileId itemId=${itemId}, executorStorageId=${executorStorageId}") {
      val query = quote {
        db.items
          .filter(_.itemId == lift(itemId.toString))
          .update(_.executorStorageId -> lift(executorStorageId))
      }
      val result = run(query)
      result > 0
    }
  }
}

@Singleton
class TempRepository @Inject() (implicit akkaRuntime: AkkaRuntime)
    extends LocalRepository(
      Files.createTempDirectory("mantik_db")
    ) {

  addShutdownHook {
    logger.debug(s"Deleting temp directory ${directory}")
    FileUtils.deleteDirectory(directory.toFile)
    Future.successful(())
  }
}

object LocalRepository {

  val DirectoryConfigKey = "mantik.repository.artifactRepository.local.directory"

}
