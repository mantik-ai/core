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

import ai.mantik.ds.FundamentalType._
import ai.mantik.ds._
import ai.mantik.ds.formats.messagepack.MessagePackAdapters.AnonymousMessagePackAdapter
import ai.mantik.ds.element._
import ai.mantik.ds.testutil.TestBase
import akka.util.ByteString
import org.msgpack.core.{MessageFormat, MessagePack}
import ai.mantik.ds.element.PrimitiveEncoder._

import scala.collection.immutable.ListMap

class MessagePackAdaptersSpec extends TestBase {

  val samples: Seq[(DataType, Element)] = TypeSamples.fundamentalSamples ++ Seq(
    Image(
      4,
      5,
      ListMap(
        ImageChannel.Black -> ImageComponent(Uint8)
      )
    ) -> ImageElement(
      ByteString((for (i <- 0 until 20) yield i.toByte): _*)
    )
  )

  /** Test serialization, returns the serialized message pack code. */
  private def testSerializationAndBack(adapter: AnonymousMessagePackAdapter, sample: Element): Array[Byte] = {
    val messagePacker = MessagePack.newDefaultBufferPacker()
    adapter.elementWriter(messagePacker, sample)
    val result = messagePacker.toByteArray
    val messageUnpacker = MessagePack.newDefaultUnpacker(result)
    val back = adapter.read(messageUnpacker)
    back shouldBe sample
    result
  }

  for ((dataType, sample) <- samples) {
    it should s"encode ${dataType} (${sample})" in {
      val adapter = MessagePackAdapters.lookupAdapter(dataType)
      testSerializationAndBack(adapter, sample)
    }
  }

  it should "decode float32 encapsulated in float64" in {
    // Workaround #61
    val bytes = ByteString(0xcb, 0x40, 0x54, 0x7f, 0xa9, 0x80, 0x00, 0x00, 0x00)
    val adapter = MessagePackAdapters.lookupAdapter(FundamentalType.Float32)
    val unpacker = MessagePack.newDefaultUnpacker(bytes.toArray)
    adapter.read(unpacker) shouldBe ValueEncoder.wrap(81.9947204589844.toFloat)
  }

  it should "encode uint8 correctly" in {
    // internally we encode signed, but in transport it should use the correct type.
    val value = Uint8.wrap(255.toByte)
    val adapter = MessagePackAdapters.lookupAdapter(Uint8)
    val encoded = testSerializationAndBack(adapter, value)
    val unpacker = MessagePack.newDefaultUnpacker(encoded)
    val nextFormat = unpacker.getNextFormat
    nextFormat shouldBe MessageFormat.UINT8

    for {
      x <- Seq(0, -10, -20, -40, -80, -120, 20, 40, 60, 80, 100)
    } {
      testSerializationAndBack(
        adapter,
        Uint8.wrap(x.toByte)
      )
    }
  }

  it should "encode uint32 correctly" in {
    // internally we encode signed, but in transport it should use the correct type.
    // -1 is then UInt32.MAX
    val value = Uint32.wrap(-1.toInt)
    val adapter = MessagePackAdapters.lookupAdapter(Uint32)
    val encoded = testSerializationAndBack(adapter, value)
    val unpacker = MessagePack.newDefaultUnpacker(encoded)
    val nextFormat = unpacker.getNextFormat
    nextFormat shouldBe MessageFormat.UINT32

    for {
      x <- Seq(0, -10, -20, -40, -80, -120, -240, -4000, -8000, -16000, -32000, -64000, -5000000, 20, 40, 60, 80, 100,
        500000)
    } {
      testSerializationAndBack(
        adapter,
        Uint32.wrap(x)
      )
    }
  }

  it should "encode uint64 correctly" in {
    // internally we encode signed, but in transport it should use the correct type.
    // -1 is then UInt64.MAX
    val value = Uint64.wrap(-1.toLong)
    val adapter = MessagePackAdapters.lookupAdapter(Uint64)
    val encoded = testSerializationAndBack(adapter, value)
    val unpacker = MessagePack.newDefaultUnpacker(encoded)
    val nextFormat = unpacker.getNextFormat
    nextFormat shouldBe MessageFormat.UINT64

    for {
      x <- Seq(0, -10, -20, -40, -80, -120, -24000, -4000, -8000, -16000, -32000, -64000, -50000000, 20, 40, 60, 80,
        100, 500000)
    } {
      testSerializationAndBack(
        adapter,
        Uint64.wrap(x)
      )
    }
  }

  it should "provide a context for tables" in {
    val table = TabularData(
      "x" -> StringType,
      "y" -> Int32
    )
    val rows = Seq(
      TabularRow(StringType.wrap("Hello World"), Int32.wrap(123)),
      TabularRow(StringType.wrap("Boom"), Int32.wrap(-2))
    )
    val context = MessagePackAdapters.createRootElementContext(table)
    val messagePacker = MessagePack.newDefaultBufferPacker()
    rows.foreach { row => context.write(messagePacker, row) }
    val result = messagePacker.toByteArray
    val messageUnpacker = MessagePack.newDefaultUnpacker(result)
    val backBuilder = Vector.newBuilder[TabularRow]
    for (i <- 0 until 2) {
      backBuilder += context.read(messageUnpacker).asInstanceOf[TabularRow]
    }
    backBuilder.result() shouldBe rows
  }

  it should "work for embedded tables" in {
    val dataType = TabularData(
      "name" -> StringType,
      "y" -> TabularData(
        "subx" -> Int32,
        "suby" -> Float64
      )
    )

    val element = EmbeddedTabularElement(
      TabularRow(
        StringType.wrap("Number1"),
        EmbeddedTabularElement(
          TabularRow(
            Int32.wrap(4),
            Float64.wrap(12.4)
          ),
          TabularRow(
            Int32.wrap(2),
            Float64.wrap(100.5)
          )
        )
      ),
      TabularRow(
        StringType.wrap("Number2"),
        EmbeddedTabularElement()
      ),
      TabularRow(
        StringType.wrap("Number3"),
        EmbeddedTabularElement(
          TabularRow(
            Int32.wrap(1),
            Float64.wrap(10.3)
          )
        )
      )
    )
    val adapter = MessagePackAdapters.lookupAdapter(dataType)
    testSerializationAndBack(adapter, element)
  }

  "tensors" should "should serialize well" in {
    val tensor = Tensor(
      Int32,
      Seq(2, 3)
    )
    val value = TensorElement[Int](
      IndexedSeq(1, 2, 3, 4, 5, 6)
    )
    val adapter = MessagePackAdapters.lookupAdapter(tensor)
    testSerializationAndBack(adapter, value)
  }

  it should "fail on incompatible types" in {
    val tensor = Tensor(
      Int32,
      Seq(2, 2)
    )
    val value = TensorElement[Double](
      IndexedSeq(1, 2, 3, 4)
    )
    val adapter = MessagePackAdapters.lookupAdapter(tensor)
    intercept[ClassCastException] {
      testSerializationAndBack(adapter, value)
    }
  }

  it should "fail on incompatible shape" in {
    val tensor = Tensor(
      Int32,
      Seq(2, 3)
    )
    val value = TensorElement[Int](
      IndexedSeq(1, 2, 3)
    )
    val adapter = MessagePackAdapters.lookupAdapter(tensor)
    intercept[IllegalArgumentException] {
      testSerializationAndBack(adapter, value)
    }.getMessage should include("element count mismatch")
  }

  "nullables" should "serialize well" in {
    val base = Nullable(FundamentalType.Int32)
    val elements = Seq(NullElement, SomeElement(Primitive(100)))
    val adapter = MessagePackAdapters.lookupAdapter(base)
    for {
      e <- elements
    } {
      testSerializationAndBack(adapter, e)
    }
  }

  "lists" should "serialize well" in {
    val adapter = MessagePackAdapters.lookupAdapter(TypeSamples.array._1)
    testSerializationAndBack(adapter, TypeSamples.array._2)
  }

  "named tuples" should "serialize well" in {
    val adapter = MessagePackAdapters.lookupAdapter(TypeSamples.namedTuple._1)
    testSerializationAndBack(adapter, TypeSamples.namedTuple._2)
  }
}
