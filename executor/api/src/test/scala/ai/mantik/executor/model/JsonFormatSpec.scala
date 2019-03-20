package ai.mantik.executor.model
import io.circe.Json
import io.circe.syntax._

class JsonFormatSpec extends TestBase {

  val complexJob = Job(
    isolationSpace = "user1",
    graph = Graph(
      nodes = Map(
        "loader" -> Node.source(
          ContainerService(
            main = Container(
              image = "mantik:dataset_loader",
              parameters = Seq("mantik-resource-url1")
            ),
            ioAffine = true
          )
        ),
        "algo1" -> Node.transformer(
          ContainerService(
            main = Container(
              image = "mantik:algorithm"
            ),
            dataProvider = Some(Container(
              image = "mantik:algorithm_loader",
              parameters = Seq("mantik-algorithm-id")
            ))
          )
        ),
        "saver" -> Node.sink(
          ExistingService(
            "http://some-url/resource"
          )
        )
      ),
      links = Seq(
        Link(NodeResourceRef.source("loader") -> NodeResourceRef.transformation("algo1")),
        Link(NodeResourceRef.transformation("algo1") -> NodeResourceRef.sink("saver"))
      )
    )
  )

  "job" should "serialize and deserialize well" in {
    val json = complexJob.asJson
    // println(s"JSON=${json}")
    val back = json.as[Job].getOrElse(fail)
    back shouldBe complexJob
  }

  "jobState" should "serialize well" in {
    val states: Seq[JobState] = Seq(JobState.Finished, JobState.Failed, JobState.Pending, JobState.Running)
    states should contain theSameElementsAs (JobState.All)
    states.foreach { s =>
      s.asJson.as[JobState].getOrElse(fail) shouldBe s
    }
    Json.fromString("unknown").as[JobState] shouldBe 'left
    Json.fromInt(2).as[JobState] shouldBe 'left
  }
}
