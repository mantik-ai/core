package ai.mantik.executor.docker.integration

import ai.mantik.executor.common.test.SampleJobs
import ai.mantik.executor.docker.api.DockerClient
import ai.mantik.executor.docker.{DockerExecutor, DockerExecutorConfig}
import ai.mantik.executor.model.JobState
import ai.mantik.executor.model.docker.DockerConfig

class SimpleJobSpec extends IntegrationTestBase {

  val job = SampleJobs.job

  trait Env {
    val dockerClient = new DockerClient()
    val executorConfig = DockerExecutorConfig.fromTypesafeConfig(typesafeConfig)
    val dockerExecutor = new DockerExecutor(dockerClient, executorConfig)
  }

  lazy val dockerConfig = DockerConfig.parseFromConfig(typesafeConfig.getConfig("mantik.executor.docker"))

  it should "start a simple job" in new Env {
    val response = await(dockerExecutor.schedule(job))
    response shouldNot be(empty)

    val state = eventually {
      val state = await(dockerExecutor.status(job.isolationSpace, response)).state
      state shouldBe 'terminal
      state
    }
    state shouldBe JobState.Finished
  }
}
