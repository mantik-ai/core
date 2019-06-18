package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, DataProvider, DeployServiceRequest, ExecutorModelDefaults }
import ai.mantik.testutils.TestBase
import skuber.{ LabelSelector, RestartPolicy }
import skuber.Service.Port

class KubernetesServiceConverterSpec extends TestBase {

  trait Env {
    val config = Config()
    val id = "Id1"
    val serviceRequest = DeployServiceRequest(
      "myservice",
      "isospace",
      ContainerService(
        Container("foo1"),
        dataProvider = Some(DataProvider(
          url = Some("http://my_data")
        ))
      )
    )
    val converter = new KubernetesServiceConverter(
      config,
      id,
      serviceRequest
    )
  }

  "service" should "be ok" in new Env {
    val labels = converter.service.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe id
    labels(KubernetesConstants.ServiceNameLabel) shouldBe "myservice"
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId

    val serviceSpec = converter.service.spec.get
    serviceSpec.ports shouldBe List(
      Port(port = 80, targetPort = Some(Left(ExecutorModelDefaults.Port)))
    )

    serviceSpec.selector shouldBe Map(
      KubernetesConstants.ServiceIdLabel -> id
    )
  }

  "replicaSet" should "be ok" in new Env {
    val rs = converter.replicaSet
    rs.name shouldBe "service-id1-rs"
    val labels = rs.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe id
    labels(KubernetesConstants.ServiceNameLabel) shouldBe "myservice"
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId
    rs.spec.get.selector shouldBe LabelSelector(
      LabelSelector.IsEqualRequirement(KubernetesConstants.ServiceIdLabel, id)
    )
  }

  "podTemplateSpec" should "be ok" in new Env {
    val podTemplate = converter.podTemplateSpec

    val labels = podTemplate.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe id
    labels(KubernetesConstants.ServiceNameLabel) shouldBe "myservice"
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId
  }

  "podSpec" should "not contain a sidecar" in new Env {
    converter.podSpec.containers.map(_.name) shouldBe Seq("main")
    converter.podSpec.initContainers.map(_.name) shouldBe Seq("data-provider")
  }

  it should "set RestartPolicy to always" in new Env {
    converter.podSpec.restartPolicy shouldBe RestartPolicy.Always
  }
}
