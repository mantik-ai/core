package ai.mantik.executor.common

import ai.mantik.testutils.TestBase
import io.circe.parser
import io.circe.syntax._

class CoordinatorPlanSpec extends TestBase {

  it should "deserialize/serialize fine" in {
    val json =
      """
        |{
        |	"nodes":{"A":{"address":"localhost:50501"}, "B":{"address":"localhost:50502"}, "C":{"url":"http://file-service", "quitAfterwards": true}},
        |	"flows":[[{"node": "A", "resource": "in", "contentType": "application/x-mantik-bundle"}, {"node": "B", "resource": "out"}, {"node": "C", "resource":"final"}]]
        |}
      """.stripMargin
    val parsedJson = parser.parse(json).forceRight
    val plan = parsedJson.as[CoordinatorPlan].forceRight
    plan shouldBe CoordinatorPlan(
      nodes = Map(
        "A" -> CoordinatorPlan.Node(Some("localhost:50501")),
        "B" -> CoordinatorPlan.Node(Some("localhost:50502")),
        "C" -> CoordinatorPlan.Node(url = Some("http://file-service"), quitAfterwards = Some(true))
      ),
      flows = Seq(
        Seq(
          CoordinatorPlan.NodeResourceRef("A", "in", Some("application/x-mantik-bundle")),
          CoordinatorPlan.NodeResourceRef("B", "out", None),
          CoordinatorPlan.NodeResourceRef("C", "final", None)
        )
      )
    )
    plan.asJson.as[CoordinatorPlan].right.get shouldBe plan
  }
}

