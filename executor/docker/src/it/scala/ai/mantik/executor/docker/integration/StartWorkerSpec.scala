package ai.mantik.executor.docker.integration

import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.common.test.integration.StartWorkerSpecBase
import ai.mantik.executor.docker.DockerConstants

class StartWorkerSpec extends IntegrationTestBase with StartWorkerSpecBase {
  override protected def checkEmptyNow(): Unit = {
    val containers = await(dockerClient.listContainers(true))
    containers.filter(_.Labels.get(LabelConstants.UserIdLabelName).contains(userId)) shouldBe empty
  }
}
