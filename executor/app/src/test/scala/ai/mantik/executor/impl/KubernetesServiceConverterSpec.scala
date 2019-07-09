package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model.docker.Container
import ai.mantik.executor.model.{ ContainerService, DataProvider, DeployServiceRequest, ExecutorModelDefaults }
import ai.mantik.testutils.TestBase
import skuber.{ LabelSelector, RestartPolicy }
import skuber.Service.Port

class KubernetesServiceConverterSpec extends TestBase {

  trait Env {
    lazy val config = Config()
    lazy val serviceId = "service1IdÄ" // something to escape inside!
    lazy val serviceIdEscaped = KubernetesNamer.encodeLabelValue(serviceId)
    lazy val nameHint: Option[String] = Some("candidate")
    lazy val serviceRequest = DeployServiceRequest(
      serviceId,
      nameHint,
      "isospace",
      ContainerService(
        Container("foo1"),
        dataProvider = Some(DataProvider(
          url = Some("http://my_data")
        ))
      )
    )
    lazy val converter = new KubernetesServiceConverter(
      config,
      serviceRequest
    )
  }

  "service" should "be ok" in new Env {
    val labels = converter.service.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe serviceIdEscaped
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId

    converter.service.name shouldBe empty
    converter.service.metadata.generateName shouldBe nameHint.get

    val serviceSpec = converter.service.spec.get
    serviceSpec.ports shouldBe List(
      Port(port = 80, targetPort = Some(Left(ExecutorModelDefaults.Port)))
    )

    serviceSpec.selector shouldBe Map(
      KubernetesConstants.ServiceIdLabel -> serviceIdEscaped
    )
  }

  it should "not have a name if there is a name hint" in new Env {
    override lazy val nameHint = None
    val service = converter.service
    service.name shouldBe converter.namer.serviceName
  }

  "replicaSet" should "be ok" in new Env {
    val rs = converter.replicaSet
    rs.name shouldBe "service-service1id-rs"
    val labels = rs.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe serviceIdEscaped
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId
    rs.spec.get.selector shouldBe LabelSelector(
      LabelSelector.IsEqualRequirement(KubernetesConstants.ServiceIdLabel, serviceIdEscaped)
    )
  }

  "podTemplateSpec" should "be ok" in new Env {
    val podTemplate = converter.podTemplateSpec

    val labels = podTemplate.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe serviceIdEscaped
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
