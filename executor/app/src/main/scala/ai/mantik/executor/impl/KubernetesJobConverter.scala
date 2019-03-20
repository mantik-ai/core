package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model._
import skuber.{ ConfigMap, Container, EnvFromSource, ObjectMeta, Pod, RestartPolicy, Volume }
import io.circe.syntax._

object KubernetesJobConverter {
  /** Label used to save the job id in Kubernetes Resources. */
  val JobIdLabel = "jobId"

  /** Label used to save the label id in Kubernetes Resources. */
  val TrackerIdLabel = "trackerId"

  /** NAme of the coordinator container inside a pod. */
  val CoordinatorContainerName = "coordinator"
  /** Name of the sidecar container inside a pod. */
  val SidecarContainerName = "sidecar"

  /** Names the role of pods, value CoordinatorRole or WorkerRole */
  val RoleName = "role"

  val CoordinatorRole = "coordinator"
  val WorkerRole = "worker"
}

/** Translates Mantik Execution Model into Kubernetes objects. */
class KubernetesJobConverter(config: Config, job: Job, jobId: String) {

  private val analysis = new GraphAnalysis(job.graph)
  private val defaultLabels = Map(
    KubernetesJobConverter.JobIdLabel -> jobId,
    KubernetesJobConverter.TrackerIdLabel -> config.podTrackerId
  )
  private val namer = new KubernetesNamer(jobId)

  /** Generate the Pod Definitions. */
  lazy val pods: Seq[Pod] = {
    job.graph.nodes.map {
      case (name, node) =>
        convertNode(name, node)
    }.toSeq
  }

  /** Generate the main config map. */
  def configuration(podIpAdresses: Map[String, String]): ConfigMap = {
    val plan = coordinatorPlan(podIpAdresses)
    val configData = Map(
      "plan" -> plan.asJson.toString()
    )

    ConfigMap(
      data = configData,
      metadata = ObjectMeta(
        labels = defaultLabels,
        name = namer.configName
      )
    )
  }

  def coordinatorPlan(podIpAdresses: Map[String, String]): CoordinatorPlan = {
    val nodes = job.graph.nodes.map {
      case (nodeName, node) =>
        val podName = namer.podName(nodeName)
        val podIp = podIpAdresses.get(podName).getOrElse {
          throw new IllegalStateException(s"Could not get ip adress of pod $podName of node $nodeName")
        }
        nodeName -> CoordinatorPlan.Node(podIp + ":8503") // TODO Configurable side car port
    }
    CoordinatorPlan(
      nodes,
      flows = analysis.flows.map { flow =>
        flow.nodes
      }.toSeq
    )
  }

  def convertCoodinator: skuber.batch.Job = {
    skuber.batch.Job(
      metadata = ObjectMeta(
        name = namer.jobName,
        labels = defaultLabels
      ),
      spec = Some(
        skuber.batch.Job.Spec(
          backoffLimit = Some(0),
          template = Some(
            Pod.Template.Spec(
              metadata = ObjectMeta(
                labels = defaultLabels ++ Map(
                  KubernetesJobConverter.RoleName -> KubernetesJobConverter.CoordinatorRole
                )
              ),
              spec = Some(
                Pod.Spec(
                  containers = List(
                    Container(
                      name = KubernetesJobConverter.CoordinatorContainerName,
                      image = config.coordinator.image,
                      args = (config.coordinator.parameters ++ Seq("-planFile", "/config/plan")).toList,
                      volumeMounts = List(
                        Volume.Mount(
                          "config-volume", mountPath = "/config"
                        )
                      ),
                      env = List(
                        // Coordinator needs its IP Address
                        skuber.EnvVar(
                          "COORDINATOR_IP",
                          skuber.EnvVar.FieldRef("status.podIP")
                        )
                      )
                    )
                  ),
                  volumes = List(
                    Volume(
                      "config-volume",
                      Volume.ConfigMapVolumeSource(
                        namer.configName
                      )
                    )
                  ),
                  restartPolicy = RestartPolicy.Never
                )
              )
            )
          )
        )
      )
    )
  }

  def convertNode(nodeName: String, node: Node): Pod = {
    val sideCar = createSidecar(node)

    // TODO: IoAffinity!

    val containers = node.service match {
      case ct: ContainerService =>
        val main = Container(
          name = "main",
          image = ct.main.image,
          volumeMounts = ct.dataProvider.map { dataProvider =>
            List(Volume.Mount(name = "data", mountPath = "/data"))
          }.getOrElse(Nil)
        )
        // TODO: Data Provider as init container?
        val dataProvider = ct.dataProvider.map { dataProvider =>
          Container(
            name = "data_provider",
            image = dataProvider.image,
            args = dataProvider.parameters.toList,
            volumeMounts = List(
              Volume.Mount(name = "data", mountPath = "/data")
            )
          )
        }.toList
        main :: dataProvider
      case et: ExistingService =>
        Nil
    }

    val spec = Pod.Spec(
      containers = sideCar :: containers,
      restartPolicy = RestartPolicy.Never
    )
    Pod(
      metadata = ObjectMeta(
        name = namer.podName(nodeName),
        labels = defaultLabels ++ Map(
          KubernetesJobConverter.RoleName -> KubernetesJobConverter.WorkerRole
        )
      ),
      spec = Some(spec)
    )
  }

  private def createSidecar(node: Node): Container = {
    // TODO: SideCars listen per default on Port 8503
    // But we have no mechanism to prevent clashers, if the service listens on the same port

    val shutdownParameters = node.service match {
      case e: ExistingService => Nil
      case _                  => List("-shutdown") // shutdown the http server after quitting the sidecar.
    }

    Container(
      name = KubernetesJobConverter.SidecarContainerName,
      image = config.sideCar.image,
      args = config.sideCar.parameters.toList ++ List("-url", urlForSideCar(node.service)) ++ shutdownParameters
    )
  }

  private def urlForSideCar(nodeService: NodeService): String = {
    nodeService match {
      case ExistingService(url) => url
      case c: ContainerService  => s"http://localhost:${c.port}"
    }
  }

}
