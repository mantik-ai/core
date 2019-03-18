package ai.mantik.ds.natural

import ai.mantik.ds.TypeSamples
import ai.mantik.ds.testutil.TestBase

class PrimitiveEncoderSpec extends TestBase {

  it should "encode and decode all samples" in {
    TypeSamples.fundamentalSamples.foreach {
      case (typeName, sample) =>
        val encoder = PrimitiveEncoder.lookup(typeName)
        withClue(s"It must work for ${typeName}") {
          encoder.wrap(sample.x.asInstanceOf[encoder.ScalaType]) shouldBe sample
          encoder.unwrap(sample) shouldBe sample.x
        }
    }
  }

  it should "decode from string" in {
    TypeSamples.fundamentalSamples.foreach {
      case (typeName, sample) =>
        val s = sample.x.toString
        val encoder = PrimitiveEncoder.lookup(typeName)
        withClue(s"It must work for ${typeName}") {
          encoder.convert.isDefinedAt(s) shouldBe true
          encoder.convert(s) shouldBe sample.x
        }
    }
  }

  it should "decode from their own type" in {
    TypeSamples.fundamentalSamples.foreach {
      case (typeName, sample) =>
        val encoder = PrimitiveEncoder.lookup(typeName)
        withClue(s"It must work for ${typeName}") {
          encoder.convert.isDefinedAt(sample.x) shouldBe true
          encoder.convert(sample.x) shouldBe sample.x
        }
    }
  }
}
