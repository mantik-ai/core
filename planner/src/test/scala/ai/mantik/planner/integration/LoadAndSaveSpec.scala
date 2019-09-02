package ai.mantik.planner.integration

import ai.mantik.ds.element.Bundle
import ai.mantik.elements.NamedMantikId
import ai.mantik.planner.DataSet
import ai.mantik.testutils.tags.IntegrationTest

@IntegrationTest
class LoadAndSaveSpec extends IntegrationTestBase {

  it should "be possible to load and save an item" in {

    val item = DataSet.literal(Bundle.fundamental(100))
      .tag("item1")

    context.execute(item.save())

    val item2 = context.loadDataSet("item1")

    item2.mantikId shouldBe item.mantikId
    item2.itemId shouldBe item.itemId

    withClue("It should be possible to save with another name again") {
      context.execute(item2.tag("other_name").save())

      val item3 = context.loadDataSet("other_name")
      item3.mantikId shouldBe NamedMantikId("other_name")
      item3.itemId shouldBe item.itemId
    }
  }
}
