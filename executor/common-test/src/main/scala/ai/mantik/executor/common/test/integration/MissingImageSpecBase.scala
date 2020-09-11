package ai.mantik.executor.common.test.integration

import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ListWorkerRequest, MnpWorkerDefinition, StartWorkerRequest, WorkerState }
import ai.mantik.testutils.TestBase

import scala.util.{ Failure, Success, Try }

trait MissingImageSpecBase {
  self: IntegrationBase with TestBase =>

  val simpleStartWorker = StartWorkerRequest(
    isolationSpace = "missing1",
    id = "user1",
    definition = MnpWorkerDefinition(
      container = Container(
        image = "missing_image1"
      )
    )
  )

  it should "fail correctly for missing images." in withExecutor { executor =>
    // It may either fail immediately or go into error stage later
    Try {
      await(executor.startWorker(simpleStartWorker))
    } match {
      case Failure(error) if error.getMessage.toLowerCase.contains("no such image") =>
      // ok (docker acts this way)
      case Failure(error) =>
        fail("Unexpected error", error)
      case Success(_) =>
        eventually {
          val listing = await(executor.listWorkers(ListWorkerRequest(
            isolationSpace = "missing1",
            idFilter = Some("user1")
          )))
          listing.workers.size shouldBe 1
          listing.workers.head.state shouldBe an[WorkerState.Failed]
          listing.workers.head.state.asInstanceOf[WorkerState.Failed].error.get should include("image error")
        }
    }
  }
}
