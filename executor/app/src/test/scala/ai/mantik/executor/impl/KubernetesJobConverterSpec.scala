package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model._
import ai.mantik.executor.model.docker.{ Container, DockerConfig, DockerLogin }
import ai.mantik.testutils.TestBase
import skuber.{ RestartPolicy, Volume }
import io.circe.syntax._

class KubernetesJobConverterSpec extends TestBase {

  trait Env {
    private val oldConfig = Config()
    def config = oldConfig.copy(
      sideCar = Container("my_sidecar", Seq("sidecar_arg")),
      coordinator = Container("my_coordinator", Seq("coordinator_arg")),
      payloadPreparer = Container("payload_preparer"),
      kubernetes = oldConfig.kubernetes.copy(
        namespacePrefix = "systemtest-",
      ),
      podTrackerId = "mantik-executor",
      dockerConfig = DockerConfig(
        defaultImageTag = Some("mytag"),
        defaultImageRepository = Some("my-repo"),
        logins = Seq(
          DockerLogin("repo1", "user1", "password1")
        )
      )
    )
  }

  trait SimpleAbEnv extends Env {
    val job = Job(
      "helloworld",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "executor_sample_source"
              )
            ), Some("contentType1")
          ),
          "B" -> Node.sink(
            ContainerService(
              main = Container(
                image = "executor_sample_sink"
              )
            ), Some("contentType2")
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
        )
      ),
      extraLogins = Seq(
        DockerLogin("repo2", "user2", "password2")
      )
    )

    lazy val converter = new KubernetesJobConverter(config, job, "job1")
    lazy val podNameA = converter.namer.podName("A")
    lazy val podNameB = converter.namer.podName("B")
    lazy val ipMapping = Map(
      podNameA -> "192.168.1.1",
      podNameB -> "192.168.1.2"
    )
  }

  trait SimpleAbExistingEnv extends Env {
    val job = Job(
      "helloworld",
      graph = Graph(
        nodes = Map(
          "A" -> Node.source(
            ContainerService(
              main = Container(
                image = "executor_sample_source"
              )
            ), Some("contentType1")
          ),
          "B" -> Node.sink(
            ExistingService("http://external-service"), Some("contentType2")
          )
        ),
        links = Link.links(
          NodeResourceRef("A", ExecutorModelDefaults.SourceResource) -> NodeResourceRef("B", ExecutorModelDefaults.SinkResource)
        )
      )
    )

    lazy val enableCollapse = true
    override def config: Config = super.config.copy(enableExistingServiceNodeCollapse = enableCollapse)
    lazy val converter = new KubernetesJobConverter(config, job, "job1")
    lazy val podNameA = converter.namer.podName("A")
    lazy val podNameB = converter.namer.podName("B")
    lazy val ipMapping = Map(
      podNameA -> "192.168.1.1",
      podNameB -> "192.168.1.2"
    )
  }

  it should "create nice pods" in new SimpleAbEnv {
    val pods = converter.pods
    pods.size shouldBe 2
    withClue("It should have disabled restart policy") {
      pods.foreach { pod =>
        pod.spec.get.restartPolicy shouldBe RestartPolicy.Never
      }
    }
    withClue("It should all have the job embedded") {
      pods.foreach { pod =>
        val labels = pod.metadata.labels
        labels shouldBe Map(
          "jobId" -> "job1",
          "trackerId" -> config.podTrackerId,
          "role" -> KubernetesConstants.WorkerRole,
          KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
        )
      }
    }
    withClue("It should embed a sidecar for every one") {
      pods.foreach { pod =>
        val spec = pod.spec.get
        spec.containers.size shouldBe 2
        val sidecar = spec.containers.find(_.name == "sidecar").get
        sidecar.image shouldBe config.sideCar.image
        sidecar.args shouldBe Seq("sidecar_arg", "-url", "http://localhost:8502", "-shutdown")
      }
    }
  }

  it should "create a coordinator plan" in new SimpleAbEnv {
    converter.coordinatorPlan(ipMapping) shouldBe CoordinatorPlan(
      nodes = Map(
        "A" -> CoordinatorPlan.Node(Some("192.168.1.1:8503")),
        "B" -> CoordinatorPlan.Node(Some("192.168.1.2:8503"))
      ),
      flows = Seq(
        Seq(CoordinatorPlan.NodeResourceRef("A", "out", Some("contentType1")), CoordinatorPlan.NodeResourceRef("B", "in", Some("contentType2")))
      )
    )
  }

  it should "create a nice config ConfigMap" in new SimpleAbEnv {
    val configMap = converter.configuration(ipMapping)
    configMap.metadata.name shouldBe converter.namer.configName
    configMap.data shouldBe Map(
      "plan" -> converter.coordinatorPlan(ipMapping).asJson.toString
    )
  }

  it should "create a nice job" in new SimpleAbEnv {
    val kubernetesJob = converter.convertCoordinator
    kubernetesJob.metadata.name shouldBe converter.namer.jobName
    kubernetesJob.metadata.labels shouldBe Map(
      "jobId" -> "job1",
      "trackerId" -> config.podTrackerId,
      KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
    )
    kubernetesJob.spec.get.backoffLimit shouldBe Some(0)
    val podTemplate = kubernetesJob.spec.get.template.get
    podTemplate.metadata.labels shouldBe Map(
      "jobId" -> "job1",
      "trackerId" -> config.podTrackerId,
      "role" -> KubernetesConstants.CoordinatorRole,
      KubernetesConstants.ManagedLabel -> KubernetesConstants.ManagedValue
    )
    val podSpec = podTemplate.spec.get
    podSpec.restartPolicy shouldBe RestartPolicy.Never
    podSpec.containers.size shouldBe 1
    val container = podSpec.containers.head
    container.image shouldBe config.coordinator.image
    container.args shouldBe config.coordinator.parameters ++ List("-plan", "@/config/plan")
    container.volumeMounts shouldBe List(
      Volume.Mount(
        "config-volume", mountPath = "/config"
      )
    )
  }

  "external services" should "be collapsed if enabled" in new SimpleAbExistingEnv {
    converter.pods.size shouldBe 1
    val converterPlan = converter.coordinatorPlan(ipMapping)
    val bNode = converterPlan.nodes.get("B").get
    bNode.address shouldBe None
    bNode.url shouldBe Some("http://external-service")
  }

  it should "not collapse if disabled" in new SimpleAbExistingEnv {
    override lazy val enableCollapse = false
    converter.pods.size shouldBe 2
    val converterPlan = converter.coordinatorPlan(ipMapping)
    val bNode = converterPlan.nodes.get("B").get
    bNode.address shouldBe Some(ipMapping(podNameB) + ":8503")
    bNode.url shouldBe None
  }
}
