package ai.mantik.planner.repository.impl

import ai.mantik.ds.FundamentalType
import ai.mantik.elements.{ DataSetDefinition, ItemId, Mantikfile, NamedMantikId }
import ai.mantik.elements.registry.api.{ ApiFileUploadResponse, ApiLoginRequest, ApiLoginResponse, ApiPrepareUploadResponse, MantikRegistryApi, MantikRegistryApiCalls }
import ai.mantik.planner.repository.{ Bridge, ContentTypes, MantikArtifact }
import ai.mantik.planner.util.TestBaseWithAkkaRuntime
import ai.mantik.testutils.TestBase
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import net.reactivecore.fhttp.akka.{ ApiServer, ApiServerRoute }
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher

import scala.language.reflectiveCalls
import scala.concurrent.Future

class MantikRegistryImplSpec extends TestBaseWithAkkaRuntime {

  private val dummyPort = 9002

  override protected lazy val typesafeConfig: Config = ConfigFactory.load().withValue(
    "mantik.core.registry.url", ConfigValueFactory.fromAnyRef(s"http://localhost:${dummyPort}")
  )

  trait Env {
    val apiRoute = new ApiServerRoute {
      var gotLogin: ApiLoginRequest = _

      bind(MantikRegistryApiCalls.uploadFile).to {
        case (token, itemId, contentType, content) =>
          Future.successful(
            Right(ApiFileUploadResponse("file1"))
          )
      }

      bind(MantikRegistryApiCalls.prepareUpload).to {
        case (token, request) =>
          Future.successful(Right(ApiPrepareUploadResponse(Some(10))))
      }

      bind(MantikRegistryApiCalls.login).to { request =>
        gotLogin = request
        Future.successful(Right(ApiLoginResponse("Token1234", None)))
      }
    }
    val fullFakeRoute = pathPrefix("api") { apiRoute }
    val server = new ApiServer(fullFakeRoute, port = dummyPort)
    akkaRuntime.lifecycle.addShutdownHook {
      server.close()
      Future.successful(())
    }
    val client = new MantikRegistryImpl()
  }

  it should "get a nice token" in new Env {
    await(client.token()) shouldBe "Token1234"
  }

  it should "not crash on empty chunks" in new Env {
    // Workaround Akka http crashes on generating empty chunks for body parts
    // MantikRegistryClient must filter them out.
    val emptyData = Source(
      List(ByteString.fromString("a"), ByteString(), ByteString.fromString("c")
      ))
    val mantikfile = Mantikfile.pure(
      DataSetDefinition(
        bridge = Bridge.naturalBridge.mantikId,
        `type` = FundamentalType.Int32
      )
    )
    val response = await(client.addMantikArtifact(
      MantikArtifact(
        mantikfile.toJson,
        fileId = None,
        namedId = Some(NamedMantikId("hello_world")),
        itemId = ItemId.generate(),
        deploymentInfo = None
      ), payload = Some(ContentTypes.ZipFileContentType -> emptyData)
    ))
    response.fileId shouldBe Some("file1")
  }

  it should "do a custom login" in new Env {
    val response = await(client.login(
      s"http://localhost:${dummyPort}",
      "username1",
      "password1"
    ))
    apiRoute.gotLogin.name shouldBe "username1"
    apiRoute.gotLogin.password shouldBe "password1"
    response.token shouldBe "Token1234"
    response.validUntil shouldBe empty

    intercept[RuntimeException] {
      await(client.login(
        s"http://localhost:100", // unused port
        "username1",
        "password1"
      ))
    }
  }
}
