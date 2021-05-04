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
package ai.mantik.ds.helper.circe

import java.io.IOException

import akka.util.ByteString
import io.circe.{Json, JsonNumber, JsonObject}
import org.msgpack.core.{MessagePack, MessagePacker, MessageUnpacker}
import org.msgpack.value.ValueType

/** Helper for reading/writing JSON in MessagePack. */
object MessagePackJsonSupport {

  /** Write a Circe JSON into a MessagePack binary. */
  def toMessagePackBytes(in: Json): ByteString = {
    val writer = MessagePack.newDefaultBufferPacker()
    writeJsonToMessagePack(writer, in)
    ByteString.fromArrayUnsafe(writer.toByteArray)
  }

  /** Write a Circe JSON from MesagePack binary */
  def fromMessagePackBytes(in: ByteString): Json = {
    val reader = MessagePack.newDefaultUnpacker(in.toArray)
    readJsonToMessagePack(reader)
  }

  /** Write a Circe JSON into a MesagePacker. */
  def writeJsonToMessagePack(out: MessagePacker, json: Json): Unit = {
    def writeJsonObject(o: JsonObject): Unit = {
      out.packMapHeader(o.size)
      o.toIterable.foreach { case (key, value) =>
        out.packString(key)
        writeJsonValue(value)
      }
    }

    def writeJsonValue(j: Json): Unit = {
      j.fold(
        out.packNil(),
        b => out.packBoolean(b),
        num => {
          writeJsonNumber(num)
        },
        s => out.packString(s),
        a => writeJsonArray(a),
        o => writeJsonObject(o)
      )
    }

    def writeJsonNumber(n: JsonNumber): Unit = {
      n.toInt match {
        case Some(i) => out.packInt(i)
        case None =>
          n.toBigInt match {
            case Some(bigInt) => out.packBigInteger(bigInt.bigInteger)
            case None =>
              out.packDouble(n.toDouble)
          }
      }
    }

    def writeJsonArray(j: Vector[Json]): Unit = {
      out.packArrayHeader(j.size)
      j.foreach(
        writeJsonValue
      )
    }

    writeJsonValue(json)
  }

  /**
    * Read a Circe JSON from MessageUnpacker.
    * @throws IOException on unexpected format.
    */
  @throws[IOException]
  def readJsonToMessagePack(in: MessageUnpacker): Json = {
    in.getNextFormat.getValueType match {
      case ValueType.NIL =>
        in.unpackNil()
        Json.Null
      case ValueType.BOOLEAN => Json.fromBoolean(in.unpackBoolean())
      case ValueType.INTEGER => Json.fromLong(in.unpackLong())
      case ValueType.STRING  => Json.fromString(in.unpackString())
      case ValueType.ARRAY =>
        val length = in.unpackArrayHeader()
        val buffer = Vector.newBuilder[Json]
        buffer.sizeHint(length)
        for (i <- 0 until length) {
          buffer += readJsonToMessagePack(in)
        }
        Json.fromValues(buffer.result())
      case ValueType.FLOAT =>
        Json.fromDoubleOrNull(in.unpackDouble())
      case ValueType.MAP =>
        val length = in.unpackMapHeader()
        val buffer = Vector.newBuilder[(String, Json)]
        buffer.sizeHint(length)
        for (i <- 0 until length) {
          val key = in.unpackString()
          val value = readJsonToMessagePack(in)
          buffer += (key -> value)
        }
        Json.fromFields(buffer.result())
      case somethingElse =>
        throw new IOException(s"Unexpected format ${somethingElse}")
    }
  }
}
