package ai.mantik.executor.docker.integration

class GrpcProxySpec extends IntegrationTestBase {
  it should "have a grpc proxy running" in {
    withExecutor { executor =>
      val proxy = await(executor.grpcProxy("proxy_spec"))
      proxy.proxyUrl shouldBe defined
      logger.info(s"Proxy url ${proxy.proxyUrl}")
      // TODO: Check that something is responding
    }
  }
}
