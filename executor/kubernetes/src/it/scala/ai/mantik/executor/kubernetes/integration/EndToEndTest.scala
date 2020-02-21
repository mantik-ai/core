package ai.mantik.executor.kubernetes.integration

import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.common.test.SampleJobs
import ai.mantik.executor.model.JobState
import ai.mantik.executor.server.{ExecutorServer, ServerConfig}

class EndToEndTest extends IntegrationTestBase {

  trait Env extends super.Env {
    val serverConfig = ServerConfig(
      interface = "localhost",
      port = 15001
    )

    val server = new ExecutorServer(serverConfig, executor)
    val client = new ExecutorClient(s"http://localhost:${serverConfig.port}")
  }

  it should "run the hello world example" in new Env {
    val job = SampleJobs.job

    server.start()
    val jobId = await(client.schedule(job))
    jobId shouldNot be(empty)

    eventually {
      await(client.status(job.isolationSpace, jobId)).state shouldBe JobState.Finished
    }

    val logs = await(client.logs(job.isolationSpace, jobId))
    logs shouldNot be(empty)
  }
}
