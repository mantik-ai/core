package ai.mantik.executor.server

import ai.mantik.componently.{ AkkaRuntime, Component }
import ai.mantik.executor.Errors.NotFoundException
import ai.mantik.executor.client.ExecutorClient
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model._
import ai.mantik.executor.{ Errors, Executor }
import ai.mantik.testutils.{ AkkaSupport, TestBase }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest

import scala.concurrent.Future

class ExecutorServerSpec extends TestBase with AkkaSupport {
  private implicit lazy val akkaRuntime = AkkaRuntime.fromRunning(typesafeConfig)

  val config = ServerConfig(
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

  private val startWorkerRequest = StartWorkerRequest(
    "isolationspace",
    "1234",
    definition = MnpWorkerDefinition(
      container = Container(
        "foo", Seq("a")
      )
    )
  )

  private val listWorkerRequest = ListWorkerRequest(
    "isolationspace",
    Some("name"), Some("id")
  )

  private val stopWorkerRequest = StopWorkerRequest(
    "isolationspace",
    Some("name"), Some("id")
  )

  trait Env {
    var receivedPublishRequest: PublishServiceRequest = _
    var receivedStartWorkerRequest: StartWorkerRequest = _
    var receivedListWorkerRequest: ListWorkerRequest = _
    var receivedStopWorkerRequest: StopWorkerRequest = _

    lazy val executorMock = new Executor with Component {

      override implicit protected def akkaRuntime: AkkaRuntime = ExecutorServerSpec.this.akkaRuntime

      override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
        receivedPublishRequest = publishServiceRequest
        Future.successful(PublishServiceResponse("foo:1234"))
      }

      override def nameAndVersion: Future[String] = Future.successful("Super Executor v1.01")

      override def grpcProxy(isolationSpace: String): Future[GrpcProxy] = Future.successful(GrpcProxy(
        Some("http://nice-proxy.example.com:1234")
      ))

      override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] = {
        receivedStartWorkerRequest = startWorkerRequest
        Future.successful(
          StartWorkerResponse(
            "foo"
          )
        )
      }

      override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] = {
        receivedListWorkerRequest = listWorkerRequest
        Future.successful(
          ListWorkerResponse(
            Seq(
              ListWorkerResponseElement("foo", "bar", Some(Container("image")), WorkerState.Running, WorkerType.MnpWorker, None)
            )
          )
        )
      }

      override def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse] = {
        receivedStopWorkerRequest = stopWorkerRequest
        Future.successful(
          StopWorkerResponse(
            removed = Seq(
              StopWorkerResponseElement(
                "foo", "bar"
              )
            )
          )
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

  it should "serve all the regular calls" in new Env {
    server.start()
    handleServerStop {
      await(client.publishService(publishCall)).name shouldBe "foo:1234"
      receivedPublishRequest shouldBe publishCall

      await(client.nameAndVersion) shouldBe "Super Executor v1.01"

      await(client.grpcProxy("isolation1")) shouldBe await(executorMock.grpcProxy("isolation1"))

      await(client.startWorker(startWorkerRequest)) shouldBe StartWorkerResponse(
        "foo"
      )
      receivedStartWorkerRequest shouldBe startWorkerRequest

      await(client.listWorkers(listWorkerRequest)) shouldBe ListWorkerResponse(
        Seq(
          ListWorkerResponseElement("foo", "bar", Some(Container("image")), WorkerState.Running, WorkerType.MnpWorker, None)
        )
      )
      receivedListWorkerRequest shouldBe listWorkerRequest

      await(client.stopWorker(stopWorkerRequest)) shouldBe StopWorkerResponse(
        removed = Seq(
          StopWorkerResponseElement(
            "foo", "bar"
          )
        )
      )
      receivedStopWorkerRequest shouldBe stopWorkerRequest
    }
  }

  it should "handle error returns" in new Env {

    val internal = new Errors.InternalException("something went wrong")
    val notFound = new Errors.NotFoundException("not found")

    override lazy val executorMock: Executor = new Executor with Component {

      override implicit protected def akkaRuntime: AkkaRuntime = ExecutorServerSpec.this.akkaRuntime

      override def publishService(publishServiceRequest: PublishServiceRequest): Future[PublishServiceResponse] = {
        Future.failed(internal)
      }

      override def nameAndVersion: Future[String] = Future { ??? }

      override def grpcProxy(isolationSpace: String): Future[GrpcProxy] = Future.failed(notFound)

      override def startWorker(startWorkerRequest: StartWorkerRequest): Future[StartWorkerResponse] = Future.failed(internal)

      override def listWorkers(listWorkerRequest: ListWorkerRequest): Future[ListWorkerResponse] = Future.failed(internal)

      override def stopWorker(stopWorkerRequest: StopWorkerRequest): Future[StopWorkerResponse] = Future.failed(internal)
    }

    server.start()
    handleServerStop {
      intercept[Errors.InternalException] {
        await(client.publishService(publishCall))
      }
      intercept[Errors.InternalException] {
        await(client.nameAndVersion)
      }
      intercept[Errors.NotFoundException] {
        await(client.grpcProxy("isolation"))
      }
    }
  }
}
