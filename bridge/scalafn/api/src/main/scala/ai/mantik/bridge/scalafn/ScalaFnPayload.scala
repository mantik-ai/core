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

import ai.mantik.bridge.scalafn.cs.{ClassFilter, ClosureDeserializer, ClosureSerializer}
import ai.mantik.ds.{DataType, FundamentalType, TabularData}
import ai.mantik.ds.converter.Cast
import ai.mantik.ds.element.{Element, Primitive, TabularRow, ValueEncoder}
import ai.mantik.ds.functional.FunctionConverter
import akka.util.ByteString
import com.esotericsoftware.kryo.io.Input
import com.twitter.chill.ClosureCleaner
import org.apache.commons.io.FileUtils
import shapeless._

import java.io.FileOutputStream
import java.nio.file.{Files, Path}
import scala.util.control.NonFatal

/** Base trait for functions ScalaFn accepts */
trait ScalaFnPayload extends Serializable {
  def serialize(): ByteString = ScalaFnPayload.serialize(this)

  private[scalafn] def cleaned(): ScalaFnPayload
}

object ScalaFnPayload {

  /**
    * Class Filter for serialization, this classes are assumed to be
    * present inside the bridge and inside the planner.
    */
  val classFilter = ClassFilter.IgnorePrefixClassFilter(
    ClassFilter.DefaultIgnorePrefixes ++
      Seq(
        "ai.mantik.bridge.scalafn.",
        "ai.mantik.ds.",
        "ai.mantik.elements.",
        // Closure Serializer Dependencies
        "com.twitter.chill.",
        "org.apache.xbean.asm7.",
        // DS Dependencies
        "shapeless.",
        "io.circe.",
        "cats."
      )
  )

  /** Serialize ScalaFnPayload into a ByteString. */
  def serialize(o: ScalaFnPayload): ByteString = {
    val cleaned = o.cleaned()
    val serializer = new ClosureSerializer(classFilter = classFilter)
    val path = serializer.serialize(cleaned)
    try {
      ByteString.fromArrayUnsafe(Files.readAllBytes(path))
    } finally {
      Files.delete(path)
    }
  }

  /** Result of Deserialization */
  trait DeserializedPayload extends AutoCloseable {

    /** The payload. */
    def payload: ScalaFnPayload

    /** Cleanup, call if you are ready to use to delete temporary files. */
    def close(): Unit
  }

  /** Deserialize ScalaFnPayload. */
  def deserialize(bytes: ByteString): DeserializedPayload = {
    val tempFile = Files.createTempFile("mantik-scalafn-bridge", ".jar")
    val result =
      try {
        writeBytesToFile(bytes, tempFile)

        val deserializer = new ClosureDeserializer()
        val rawResult = deserializer.deserialize(tempFile)

        rawResult.asInstanceOf[ScalaFnPayload]
      } catch {
        case NonFatal(e) =>
          Files.delete(tempFile)
          throw e
      }
    new DeserializedPayload {
      override def payload: ScalaFnPayload = result

      override def close(): Unit = Files.delete(tempFile)
    }
  }

  private def writeBytesToFile(bytes: ByteString, path: Path): Unit = {
    val outputStream = new FileOutputStream(path.toFile)
    val channel = outputStream.getChannel
    try {
      bytes.asByteBuffers.foreach { byteBuffers =>
        channel.write(byteBuffers)
      }
    } finally {
      channel.close()
      outputStream.close()
    }
  }
}

case class RowMapper(
    f: TabularRow => TabularRow
) extends ScalaFnPayload {
  override private[scalafn] def cleaned() = {
    RowMapper(ClosureCleaner.clean(f))
  }
}

object RowMapper {

  /** Build a RowMapper from a function signature. */
  def build[I, O](inputData: TabularData)(f: I => O)(implicit functionConverter: FunctionConverter[I, O]): RowMapper = {
    val decoder = functionConverter.buildDecoderForTables(inputData)
    val encoder = functionConverter.buildEncoderForTables()
    val fn = decoder.andThen(f).andThen(encoder)
    RowMapper(fn)
  }
}
