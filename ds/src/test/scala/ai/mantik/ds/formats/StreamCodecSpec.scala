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
package ai.mantik.ds.formats

import ai.mantik.ds.{ DataType, FundamentalType, TypeSamples }
import ai.mantik.ds.element.{ Element, Primitive, RootElement, SingleElement, TabularBundle }
import ai.mantik.ds.formats.StreamCodec.CodecMimeType
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import akka.stream.scaladsl.{ Sink, Source }

class StreamCodecSpec extends TestBase with AkkaSupport {

  private def testEncode(dt: DataType, rootElements: Seq[RootElement], mimeType: CodecMimeType): Unit = {
    val encoder = StreamCodec.encoder(dt, mimeType.name)
    val decoder = StreamCodec.decoder(dt, mimeType.name)
    val collected = collectByteSource(Source(rootElements.toVector).via(encoder))
    val decoded = collectSource(Source.single(collected).via(decoder))
    decoded shouldBe rootElements
  }

  /** Some values are not encodeable in JSON, skip them */
  private def skip(dt: DataType, value: Element, mimeType: CodecMimeType): Boolean = {
    if (mimeType == StreamCodec.MimeJson || mimeType == StreamCodec.MimeMantikBundleJson) {
      (dt, value) match {
        case (FundamentalType.Float32, x) if x == Primitive(Float.NegativeInfinity) => true
        case (FundamentalType.Float64, x) if x == Primitive(Double.NegativeInfinity) => true
        case _ => false
      }
    } else {
      false
    }
  }

  StreamCodec.MimeTypes.foreach { mimeType =>
    s"Codec ${mimeType.name}" should "serialize and back (primitives)" in {
      TypeSamples.fundamentalSamples.foreach {
        case (dt, element) =>
          if (!skip(dt, element, mimeType)) {
            testEncode(dt, Seq(SingleElement(element)), mimeType)
          }
      }
    }

    it should "serialize and back (non tabular non primitives)" in {
      TypeSamples.nonFundamentals.foreach {
        case (dt, element) =>
          testEncode(dt, Seq(SingleElement(element)), mimeType)
      }
    }

    val tables: Seq[TabularBundle] = Seq(
      TabularBundle.build(
        "x" -> FundamentalType.Int32,
        "y" -> FundamentalType.StringType
      ).row(1, "Hello").row(2, "World").result,
      TabularBundle.build(
        "x" -> FundamentalType.Float32
      ).result
    )

    it should "serialize and back (tabular data)" in {
      tables.foreach { table =>
        testEncode(table.model, table.rows, mimeType)
      }
    }
  }
}
