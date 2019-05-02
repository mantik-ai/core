package ai.mantik.ds.converter.casthelper

import ai.mantik.ds.element.TensorElement
import ai.mantik.ds.testutil.TestBase
import ai.mantik.ds.{ Tensor, TypeSamples }

class TensorHelperSpec extends TestBase {

  it should "convert all types" in {
    for ((t, v) <- TypeSamples.fundamentalSamples) {
      val toTensor = TensorHelper.tensorPacker(t)
      val fromTensor = TensorHelper.tensorUnpacker(t)

      val converted = toTensor(IndexedSeq(v, v))
      converted.asInstanceOf[TensorElement[_]].elements shouldBe IndexedSeq(v.x, v.x)
      val back = fromTensor(converted)
      back shouldBe IndexedSeq(v, v)
    }
  }
}
