package ai.mantik.executor.server

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import ai.mantik.executor.Errors.NotFoundException
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.integration.HelloWorldSpec
import ai.mantik.executor.{ Config, Errors, Executor }
import ai.mantik.executor.model.{ Job, JobState, JobStatus, PublishServiceRequest, PublishServiceResponse }
import ai.mantik.testutils.{ AkkaSupport, TestBase }

import scala.concurrent.Future

class ExecutorServerSpec extends TestBase with AkkaSupport {

  val config = Config().copy(
    interface = "localhost",
    port = 15001
  )

  private val publishCall = PublishServiceRequest(
    "ns1",
    "service1",
    1234,
    "192.168.1.1",
    4456
  )

  trait Env {
    var receivedPublishRequest: PublishServiceRequest = _

    lazy val executorMock = new Executor {

      override def schedule(job: Job): Future[String] = Future.successful("1234")

      override def status(isolationSpace: String, id: String): Future[JobStatus] = Future.successful(JobStatus(JobState.Running))

      override def logs(isolationSpace: String, id: String): Future[String] = Future.successful("Logs")

      override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
        receivedPublishRequest = publishServiceRequest
        Future.successful(PublishServiceResponse("foo:1234"))
      }
    }
    lazy val server = new ExecutorServer(config, executorMock)
    lazy val client = new ExecutorClient("http://localhost:15001")

    def handleServerStop[T](f: => T): T = {
      try {
        f
      } finally {
        server.stop()
      }
    }
  }

  it should "render an index page" in new Env {
    server.start()
    handleServerStop {
      val res = await(Http().singleRequest(
        HttpRequest(uri = "http://localhost:15001/")
      ))
      res.status shouldBe 'success
    }
  }

  it should "server all the regular calls" in new Env {
    server.start()
    handleServerStop {
      await(client.logs("123", "345")) shouldBe "Logs"
      await(client.schedule(HelloWorldSpec.job)) shouldBe "1234"
      await(client.status("space", "1234")) shouldBe JobStatus(JobState.Running)
      await(client.publishService(publishCall)).name shouldBe "foo:1234"
      receivedPublishRequest shouldBe publishCall
    }
  }

  it should "handle error returns" in new Env {

    val internal = new Errors.InternalException("something went wrong")
    val notFound = new Errors.NotFoundException("not found")

    override lazy val executorMock: Executor = new Executor {
      override def schedule(job: Job): Future[String] = Future.failed(
        internal
      )

      override def status(isolationSpace: String, id: String): Future[JobStatus] = Future.failed(new RuntimeException("Other exception"))

      override def logs(isolationSpace: String, id: String): Future[String] = {
        Future.failed(notFound)
      }

      override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
        Future.failed(internal)
      }
    }

    server.start()
    handleServerStop {
      intercept[NotFoundException] {
        await(client.logs("123", "345"))
      }
      intercept[Errors.InternalException] {
        await(client.schedule(HelloWorldSpec.job)) shouldBe Some("1234")
      }
      // also internal error, even if exception is not registerd
      intercept[Errors.InternalException] {
        await(client.status("space", "1234")) shouldBe JobStatus(JobState.Running)
      }
      intercept[Errors.InternalException] {
        await(client.publishService(publishCall))
      }
    }
  }
}
