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
package ai.mantik.ds.formats.messagepack

import java.io.File

import ai.mantik.ds.Errors.EncodingException
import ai.mantik.ds.FundamentalType.{Int32, StringType}
import ai.mantik.ds.{DataType, TabularData}
import ai.mantik.ds.element.{Bundle, RootElement, TabularRow}
import ai.mantik.ds.testutil.{GlobalAkkaSupport, TempDirSupport, TestBase}
import akka.stream.scaladsl.{Keep, Sink, Source}
import ai.mantik.ds.element.PrimitiveEncoder._
import akka.util.ByteString

import scala.concurrent.Future

class MessagePackReaderWriterSpec extends TestBase with GlobalAkkaSupport with TempDirSupport {

  val sampleBundle = Bundle(
    TabularData(
      "x" -> Int32,
      "y" -> StringType
    ),
    Vector(
      TabularRow(
        Int32.wrap(3),
        StringType.wrap("Hello World")
      ),
      TabularRow(
        Int32.wrap(0),
        StringType.wrap("Foo")
      )
    )
  )
  it should "create readable data streams" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model)

    val source = Source(sampleBundle.rows)
    val collected = collectSource(source.via(readerWriter.encoder()).via(readerWriter.decoder()))
    collected shouldBe sampleBundle.rows
  }

  it should "be possible to auto decode the format" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model)
    val source = Source(sampleBundle.rows)

    val (futureDataType: Future[DataType], futureData: Future[Seq[RootElement]]) = source
      .via(readerWriter.encoder())
      .viaMat(MessagePackReaderWriter.autoFormatDecoder())(Keep.right)
      .toMat(Sink.seq)(Keep.both)
      .run()

    await(futureDataType) shouldBe sampleBundle.model
    await(futureData) shouldBe sampleBundle.rows
  }

  it should "report errors on empty streams" in {
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    awaitException[EncodingException] {
      Source.empty.via(decoder).runWith(Sink.seq)
    }
  }

  it should "report errors on good header and broken data" in {
    val encoded = collectByteSource(sampleBundle.encode(true))
    val source = Source(
      List(encoded, ByteString(0x4d.toByte))
    )
    val decoder = MessagePackReaderWriter.autoFormatDecoder()
    awaitException[EncodingException] {
      source.via(decoder).runWith(Sink.seq)
    }
  }

  it should "be possible to skip the encoding of the header" in {
    val withoutHeader = new MessagePackReaderWriter(sampleBundle.model, withHeader = false)
    val withHeader = new MessagePackReaderWriter(sampleBundle.model, withHeader = true)

    val source = Source(sampleBundle.rows)
    val withoutHeaderBytes = await(source.via(withoutHeader.encoder()).toMat(Sink.seq)(Keep.right).run()).reduce(_ ++ _)
    val withHeaderBytes = await(source.via(withHeader.encoder()).toMat(Sink.seq)(Keep.right).run()).reduce(_ ++ _)
    withoutHeaderBytes.size shouldBe <(withHeaderBytes.size)

    val futureData: Future[Seq[RootElement]] = source
      .via(withoutHeader.encoder())
      .viaMat(withoutHeader.decoder())(Keep.right)
      .toMat(Sink.seq)(Keep.right)
      .run()

    await(futureData) shouldBe sampleBundle.rows
  }

  "byte encoding" should "be possible to directly encode and decode bundles" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model, true)
    val bytes = readerWriter.encodeToByteString(sampleBundle.rows)
    val decoded = readerWriter.decodeFromByteString(bytes)
    decoded shouldBe sampleBundle

    val otherDecoded = await(
      Source
        .single(bytes)
        .via(readerWriter.decoder())
        .runWith(Sink.seq)
    )
    otherDecoded shouldBe sampleBundle.rows

    val directlyDecoded = MessagePackReaderWriter.autoFormatDecoderFromByteString(bytes)
    directlyDecoded shouldBe sampleBundle
  }

  it should "work without header" in {
    val readerWriter = new MessagePackReaderWriter(sampleBundle.model, false)
    val bytes = readerWriter.encodeToByteString(sampleBundle.rows)
    val decoded = readerWriter.decodeFromByteString(bytes)
    decoded shouldBe sampleBundle

    val otherDecoded = await(
      Source
        .single(bytes)
        .via(readerWriter.decoder())
        .runWith(Sink.seq)
    )
    otherDecoded shouldBe sampleBundle.rows
  }
}
