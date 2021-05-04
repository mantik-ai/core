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
package ai.mantik.planner.repository.rpc

import ai.mantik.elements.errors.{ErrorCodes, MantikException, MantikRemoteException}
import ai.mantik.elements.{ItemId, MantikId, NamedMantikId}
import akka.util.ByteString
import io.circe.{Decoder, Json}
import io.circe.jawn.JawnParser
import io.grpc.StatusRuntimeException

import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

private[mantik] object Conversions {

  def encodeMantikId(mantikId: MantikId): String = {
    mantikId.toString
  }

  def decodeMantikId(str: String): MantikId = {
    MantikId.fromString(str)
  }

  def encodeNamedMantikId(namedMantikId: NamedMantikId): String = {
    namedMantikId.toString
  }

  def decodeNamedMantikId(str: String): NamedMantikId = {
    NamedMantikId.fromString(str)
  }

  def encodeItemId(itemId: ItemId): String = {
    itemId.toString
  }

  def decodeItemId(str: String): ItemId = {
    ItemId.fromString(str)
  }

  val encodeErrors: PartialFunction[Throwable, Throwable] = { case e: MantikException =>
    e.toGrpc
  }

  def encodeErrorIfPossible(e: Throwable): Throwable = {
    if (encodeErrors.isDefinedAt(e)) {
      encodeErrors.apply(e)
    } else {
      e
    }
  }

  def encodeErrorsIn[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f.recover {
      case e if encodeErrors.isDefinedAt(e) =>
        throw encodeErrors(e)
    }
  }

  val decodeErrors: PartialFunction[Throwable, Throwable] = { case e: StatusRuntimeException =>
    MantikRemoteException.fromGrpc(e)
  }

  def decodeErrorsIn[T](f: => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    f.recover {
      case e if decodeErrors.isDefinedAt(e) =>
        throw decodeErrors(e)
    }
  }

  private val jawnParser = new JawnParser()

  /**
    * Decode some serialized JSON object.
    * @param json the JSON object
    * @param msg message to throw if decoding fails (with original exception message)
    */
  def decodeJsonItem[T: Decoder](json: String, msg: String => String): T = {
    val maybeItem = jawnParser.decode[T](json)
    maybeItem match {
      case Left(error) => ErrorCodes.ProtocolError.throwIt(msg(Option(error.getMessage).getOrElse("unknown")))
      case Right(ok)   => ok
    }
  }

  /** Like decodeJsonItem but uses byte buffers to avoid copying. */
  def decodeLargeJsonItem[T: Decoder](json: ByteString, msg: String => String): T = {
    jawnParser.decodeByteBuffer[T](json.asByteBuffer) match {
      case Left(error) =>
        val firstBytes = Try(json.decodeString(StandardCharsets.UTF_8).take(100)).toOption
        val message = msg(Option(error.getMessage).getOrElse("unknown")) + s" first bytes: ${firstBytes}"
        ErrorCodes.ProtocolError.throwIt(message)
      case Right(ok) => ok
    }
  }
}
