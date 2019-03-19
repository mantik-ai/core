package io.mantik.executor.impl

import io.mantik.executor.testutils.TestBase
import io.circe.parser
import io.circe.syntax._
import io.mantik.executor.model.NodeResourceRef

class CoordinatorPlanSpec extends TestBase {

  it should "deserialize/serialize fine" in {
    val json =
      """
        |{
        |	"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}},
        |	"flows":[[{"node": "A", "resource": "in"}, {"node": "B", "resource": "out"}]],
        |	"contentType": "application/x-msgpack"
        |}
      """.stripMargin
    val parsedJson = parser.parse(json).getOrElse(fail)
    val plan = parsedJson.as[CoordinatorPlan].getOrElse(fail)
    plan shouldBe CoordinatorPlan(
      nodes = Map(
        "A" -> CoordinatorPlan.Node("localhost:50501"),
        "B" -> CoordinatorPlan.Node("localhost:50502")
      ),
      flows = Seq(
        Seq(
          NodeResourceRef("A", "in"), NodeResourceRef("B", "out")
        )
      ),
      contentType = Some("application/x-msgpack")
    )
    plan.asJson shouldBe parsedJson
  }
}
