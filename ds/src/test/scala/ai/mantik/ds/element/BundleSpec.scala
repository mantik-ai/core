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
package ai.mantik.ds.element

import java.io.File

import ai.mantik.ds.{FundamentalType, TabularData, Tensor, TypeSamples}
import ai.mantik.ds.testutil.{GlobalAkkaSupport, TempDirSupport, TestBase}
import PrimitiveEncoder._
import ai.mantik.ds.Errors.EncodingException
import ai.mantik.ds.helper.ZipUtils
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.Future
import io.circe.syntax._

class BundleSpec extends TestBase with TempDirSupport with GlobalAkkaSupport {

  val sampleBundle = Bundle(
    TabularData(
      "id" -> FundamentalType.Int32,
      "name" -> FundamentalType.StringType,
      "void" -> FundamentalType.VoidType
    ),
    Vector(
      TabularRow(FundamentalType.Int32.wrap(1), FundamentalType.StringType.wrap("Alice"), Primitive.unit),
      TabularRow(FundamentalType.Int32.wrap(2), FundamentalType.StringType.wrap("Bob"), Primitive.unit)
    )
  )

  it should "be encodable into a stream without header and back" in {
    val data = collectSource(sampleBundle.encode(withHeader = false))
    val dataAsSource: Source[ByteString, _] = Source(data.toVector)
    val sink: Sink[ByteString, Future[Bundle]] = Bundle.fromStreamWithoutHeader(sampleBundle.model)
    val back = await(dataAsSource.runWith(sink))
    back shouldBe sampleBundle

    val data2 = sampleBundle.encodeAsByteString(withHeader = false)
    data2 shouldBe data.reduce(_ ++ _)

    val back2 = Bundle.fromByteStringWithoutHeader(sampleBundle.model, data2)
    back2 shouldBe sampleBundle
  }

  it should "be encodable into a stream with header and back" in {
    val data = collectSource(sampleBundle.encode(withHeader = true))
    val dataAsSource: Source[ByteString, _] = Source(data.toVector)
    val sink: Sink[ByteString, Future[Bundle]] = Bundle.fromStreamWithHeader()
    val back = await(dataAsSource.runWith(sink))
    back shouldBe sampleBundle

    val data2 = sampleBundle.encodeAsByteString(true)
    data2 shouldBe data.reduce(_ ++ _)

    val back2 = Bundle.fromByteStringWithHeader(data2)
    back2 shouldBe sampleBundle
  }

  it should "be constructable" in {
    val bundle = TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32,
          "b" -> FundamentalType.BoolType,
          "s" -> FundamentalType.StringType,
          "n" -> FundamentalType.VoidType
        )
      )
      .row(1, true, "Hello World", ())
      .row(2, false, "How are you", ())
      .result
    val expected = Bundle(
      TabularData(
        "x" -> FundamentalType.Int32,
        "b" -> FundamentalType.BoolType,
        "s" -> FundamentalType.StringType,
        "n" -> FundamentalType.VoidType
      ),
      Vector(
        TabularRow(
          Primitive(1),
          Primitive(true),
          Primitive("Hello World"),
          Primitive.unit
        ),
        TabularRow(
          Primitive(2),
          Primitive(false),
          Primitive("How are you"),
          Primitive.unit
        )
      )
    )
    bundle shouldBe expected
  }

  it should "work with single element bundles" in {
    for ((dataType, sample) <- TypeSamples.fundamentalSamples) {
      val bundle = SingleElementBundle(dataType, sample)
      val encoded = collectByteSource(bundle.encode(true))
      val decoded = await(Source.single(encoded).runWith(Bundle.fromStreamWithHeader()))
      decoded shouldBe bundle
    }
  }

  "fundamental" should "build fundamental bundles" in {
    Bundle.fundamental(5) shouldBe SingleElementBundle(
      FundamentalType.Int32,
      Primitive(5)
    )
  }

  "single" should "return a single element if applicable" in {
    Bundle.fundamental(5).single shouldBe Some(Primitive(5))
    sampleBundle.single shouldBe None
  }

  "toString" should "render the bundle" in {
    SingleElementBundle(FundamentalType.Int32, Primitive(3)).toString shouldBe "3"
    withClue("It should not crash on illegal values") {
      val badBundle = SingleElementBundle(
        FundamentalType.Int32,
        EmbeddedTabularElement(
          TabularRow(
            TensorElement(IndexedSeq(1))
          )
        )
      )
      badBundle.toString shouldBe "<Error Bundle>"
      intercept[IllegalArgumentException] {
        badBundle.render()
      }
    }
  }

  "deserializers" should "not hang on empty data" in {
    // Regression Bug #47
    val source = Source.empty
    awaitException[EncodingException] {
      source.runWith(Bundle.fromStreamWithHeader())
    }
  }

  it should "decode empty data when there is no header needed" in {
    val bundle = await(Source.empty.runWith(Bundle.fromStreamWithoutHeader(sampleBundle.model)))
    bundle.model shouldBe sampleBundle.model
    bundle.rows shouldBe empty
  }

  it should "not hang on invalid data" in {
    // Regression Bug #47
    import scala.concurrent.duration._
    val dir = new File(getClass.getResource("/sample_directory").toURI).toPath
    val source = ZipUtils.zipDirectory(dir, 10.seconds)
    awaitException[EncodingException] {
      source.runWith(Bundle.fromStreamWithHeader())
    }
    awaitException[EncodingException] {
      source.runWith(Bundle.fromStreamWithoutHeader(sampleBundle.model))
    }
  }

  "json" should "work" in {
    // More tests in JSON Encoder implementation
    val json = sampleBundle.asJson
    val back = json.as[Bundle]
    back shouldBe Right(sampleBundle)
  }

  "cast" should "work" in {
    Bundle.fundamental(100).cast(FundamentalType.Float64) shouldBe Right(Bundle.fundamental(100.0))
    Bundle.void.cast(FundamentalType.Int32).forceLeft should include("No cast found")
    Bundle.fundamental(100).cast(FundamentalType.Float32).forceLeft should include("loose precision")
    Bundle.fundamental(100).cast(FundamentalType.Float32, allowLoosing = true) shouldBe Right(
      Bundle.fundamental(100.0f)
    )
    Bundle.fundamental("Hello").cast(FundamentalType.Int32).forceLeft should include("Cast failed")
  }

  "sorted" should "work" in {
    Bundle.fundamental(100).sorted shouldBe Bundle.fundamental(100)

    val simple = TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32
        )
      )
      .row(2)
      .row(1)
      .result

    simple.sorted shouldBe TabularBundle
      .build(
        TabularData(
          "x" -> FundamentalType.Int32
        )
      )
      .row(1)
      .row(2)
      .result

    val complex = TabularBundle.buildColumnWise
      .withPrimitives(
        "a",
        3,
        1,
        1
      )
      .withPrimitives(
        "b",
        "0",
        "a",
        "b"
      )
      .result

    val complexSorted = TabularBundle.buildColumnWise
      .withPrimitives(
        "a",
        1,
        1,
        3
      )
      .withPrimitives(
        "b",
        "a",
        "b",
        "0"
      )
      .result

    complex.sorted shouldBe TabularBundle.buildColumnWise
      .withPrimitives(
        "a",
        1,
        1,
        3
      )
      .withPrimitives(
        "b",
        "a",
        "b",
        "0"
      )
      .result
  }
}
