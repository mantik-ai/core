package ai.mantik.executor.integration
import ai.mantik.executor.Config
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.model.JobState
import ai.mantik.executor.server.ExecutorServer
import ai.mantik.executor.testutils.KubernetesIntegrationTest

@KubernetesIntegrationTest
class EndToEndTest extends IntegrationTestBase {

  trait Env extends super.Env {
    val server = new ExecutorServer(config, executor)
    val client = new ExecutorClient(s"http://localhost:${config.port}")
  }

  it should "run the hello world example" in new Env {
    val job = HelloWorldSpec.job

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
