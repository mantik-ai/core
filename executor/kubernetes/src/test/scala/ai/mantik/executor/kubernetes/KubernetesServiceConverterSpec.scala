package ai.mantik.executor.kubernetes

import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.Container
import ai.mantik.testutils.TestBase
import io.circe.Json
import skuber.Service.Port
import skuber.ext.Ingress
import skuber.{ EnvVar, LabelSelector, RestartPolicy }

class KubernetesServiceConverterSpec extends TestBase {

  trait Env {
    val defaultConfig = Config.fromTypesafeConfig(typesafeConfig)
    lazy val kubernetesConfig = defaultConfig.kubernetes.copy(
      ingressSubPath = Some("/${name}/bar"),
      ingressRemoteUrl = "http://${kubernetesHost}/${name}/bar"
    )
    lazy val config = defaultConfig.copy(
      kubernetes = kubernetesConfig
    )
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
      ),
      ingress = Some("ingress1")
    )
    lazy val converter = new KubernetesServiceConverter(
      config,
      serviceRequest,
      "192.168.1.2"
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

  "ingress" should "work in a simple example" in new Env {
    val ingress = converter.ingress("service1").get
    ingress.name shouldBe "ingress1"
    val labels = ingress.metadata.labels
    labels(KubernetesConstants.ServiceIdLabel) shouldBe serviceIdEscaped
    labels(KubernetesConstants.TrackerIdLabel) shouldBe config.podTrackerId
    val ingressSpec = ingress.spec.get
    ingressSpec shouldBe Ingress.Spec(
      rules = List(
        Ingress.Rule(
          None,
          http = Ingress.HttpRule(
            paths = List(
              Ingress.Path(
                path = "/ingress1/bar",
                backend = Ingress.Backend(
                  serviceName = "service1",
                  servicePort = 80
                )
              )
            )
          )
        )
      )
    )
    converter.ingressExternalUrl shouldBe Some("http://192.168.1.2/ingress1/bar")
  }

  it should "work in a non-path example" in new Env {
    override lazy val kubernetesConfig = defaultConfig.kubernetes.copy(
      ingressSubPath = None,
      ingressRemoteUrl = "http://${kubernetesHost}/${name}/bar"
    )
    val ingress = converter.ingress("service1").get
    val ingressSpec = ingress.spec.get
    ingressSpec shouldBe Ingress.Spec(
      backend = Some(
        Ingress.Backend(
          serviceName = "service1",
          servicePort = 80
        )
      )
    )
  }

  it should "not create an ingress if not wanted" in new Env {
    override lazy val serviceRequest = DeployServiceRequest(
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
    converter.ingress("service1") shouldBe None
    converter.ingressExternalUrl shouldBe None
  }

  "pipelines" should "also be supported" in new Env {
    override lazy val serviceRequest = DeployServiceRequest(
      serviceId,
      nameHint,
      "isospace",
      service = DeployableService.Pipeline(
        pipeline = Json.fromString("some json"),
        port = 1234
      )
    )
    val ps = converter.podSpec
    ps.restartPolicy shouldBe RestartPolicy.Always

    val container = ps.containers.ensuring(_.size == 1).head
    container.name shouldBe "controller"
    container.image shouldBe config.common.pipelineController.image
    container.args shouldBe List(
      "-port", "1234"
    )
    container.env shouldBe List(
      EnvVar(
        "PIPELINE", "\"some json\""
      )
    )
  }
}
