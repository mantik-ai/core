package ai.mantik.executor.server

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import ai.mantik.executor.Errors.NotFoundException
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.integration.HelloWorldSpec
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.{ Config, Errors, Executor }
import ai.mantik.executor.model.{ ContainerService, DeployServiceRequest, DeployServiceResponse, DeployedServicesEntry, DeployedServicesQuery, DeployedServicesResponse, Job, JobState, JobStatus, NodeService, PublishServiceRequest, PublishServiceResponse }
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

  private val deployServiceCall = DeployServiceRequest(
    "service1",
    Some("serviceName"),
    "isolation1",
    ContainerService(
      Container("Foo")
    )
  )

  private val deployedServicesQuery = DeployedServicesQuery(
    isolationSpace = "isolationSpace",
    serviceId = Some("serviceId")
  )

  private val deployedServicesResponse = DeployedServicesResponse(
    List(
      DeployedServicesEntry("id1", "http://foobar")
    )
  )

  trait Env {
    var receivedPublishRequest: PublishServiceRequest = _
    var receivedDeployServiceRequest: DeployServiceRequest = _
    var receivedDeployedServicesQuery: DeployedServicesQuery = _

    lazy val executorMock = new Executor {

      override def schedule(job: Job): Future[String] = Future.successful("1234")

      override def status(isolationSpace: String, id: String): Future[JobStatus] = Future.successful(JobStatus(JobState.Running))

      override def logs(isolationSpace: String, id: String): Future[String] = Future.successful("Logs")

      override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
        receivedPublishRequest = publishServiceRequest
        Future.successful(PublishServiceResponse("foo:1234"))
      }

      override def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse] = {
        receivedDeployServiceRequest = deployServiceRequest
        Future.successful(DeployServiceResponse("my", "my.service.name"))
      }

      override def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse] = {
        receivedDeployedServicesQuery = deployedServicesQuery
        Future.successful(
          deployedServicesResponse
        )
      }

      override def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int] = {
        receivedDeployedServicesQuery = deployedServicesQuery
        Future.successful(
          5
        )
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

      await(client.deployService(deployServiceCall)) shouldBe DeployServiceResponse("my", "my.service.name")
      receivedDeployServiceRequest shouldBe deployServiceCall

      await(client.queryDeployedServices(deployedServicesQuery)) shouldBe deployedServicesResponse
      receivedDeployedServicesQuery shouldBe deployedServicesQuery

      receivedDeployedServicesQuery = null
      await(client.deleteDeployedServices(deployedServicesQuery)) shouldBe 5
      receivedDeployedServicesQuery shouldBe deployedServicesQuery
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

      override def deployService(deployServiceRequest: DeployServiceRequest): Future[DeployServiceResponse] = {
        Future.failed(internal)
      }

      override def queryDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[DeployedServicesResponse] = {
        Future.failed(internal)
      }

      override def deleteDeployedServices(deployedServicesQuery: DeployedServicesQuery): Future[Int] = {
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
      intercept[Errors.InternalException] {
        await(client.deployService(deployServiceCall))
      }
      intercept[Errors.InternalException] {
        await(client.queryDeployedServices(deployedServicesQuery))
      }
      intercept[Errors.InternalException] {
        await(client.deleteDeployedServices(deployedServicesQuery))
      }
    }
  }
}
