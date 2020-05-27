package ai.mantik.planner.integration

import ai.mantik.executor.model.StartWorkerRequest
import ai.mantik.executor.model.docker.Container
import ai.mantik.mnp.MnpClient
import ai.mantik.planner.BuiltInItems

/** Test that we can communicate with an MNP Bridge. */
class HelloMnpBridgeSpec extends IntegrationTestBase {

  private def executor = embeddedExecutor.executor
  private val isolationSpace = "hello-mnp-bridge"
  private val MnpPort = 8502

  it should "initialize" in {
    val startResponse = await(executor.startWorker(
      StartWorkerRequest(
        isolationSpace,
        "id1",
        Container(
          BuiltInItems.SelectBridge.mantikHeader.definition.dockerImage
        )
      ))
    )
    logger.info(s"Started container ${startResponse.nodeName}")

    val grpcProxy = await(executor.grpcProxy(isolationSpace))
    logger.info(s"gRpc Proxy: ${grpcProxy}")

    val destinationAddress = s"${startResponse.nodeName}:${MnpPort}"

    Thread.sleep(2000) // Some time ot make it available

    val (channel, client) = grpcProxy.proxyUrl match {
      case Some(defined) =>
        MnpClient.connectViaProxy(defined, destinationAddress)
      case None =>
        MnpClient.connect(destinationAddress)
    }

    try {
      val response = await(client.about())
      response.name shouldNot be(empty)
    } finally  {
      channel.shutdownNow()
    }
  }
}
