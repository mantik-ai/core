package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model._
import skuber.{ ConfigMap, Container, LocalObjectReference, ObjectMeta, Pod, RestartPolicy, Volume }
import io.circe.syntax._

/** Translates Mantik Execution Model into Kubernetes objects. */
class KubernetesJobConverter(config: Config, job: Job, jobId: String) extends KubernetesConverter(
  config, jobId, job.extraLogins, "job-", KubernetesConstants.JobIdLabel
) {

  private val analysis = new GraphAnalysis(job.graph)

  /** Generate the Pod Definitions. */
  lazy val pods: Seq[Pod] = {
    job.graph.nodes.flatMap {
      case (name, node) =>
        node.service match {
          case _: ExistingService if config.enableExistingServiceNodeCollapse =>
            // do not create a Pod for it, let other nodes or coordinator access url directly
            None
          case _ =>
            Some(convertNode(name, node.service))
        }
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

  def coordinatorPlan(podIpAddresses: Map[String, String]): CoordinatorPlan = {
    val nodes = job.graph.nodes.map {
      case (nodeName, node) =>
        node.service match {
          case e: ExistingService if config.enableExistingServiceNodeCollapse =>
            nodeName -> CoordinatorPlan.Node(url = Some(e.url))
          case _ =>
            val podName = namer.podName(nodeName)
            val podIp = podIpAddresses.get(podName).getOrElse {
              throw new IllegalStateException(s"Could not get ip adress of pod $podName of node $nodeName")
            }
            val address = podIp + ":8503" // TODO Configurable side car port
            nodeName -> CoordinatorPlan.Node(address = Some(address))
        }
    }

    CoordinatorPlan(
      nodes,
      flows = analysis.flows.map { flow =>
        flow.nodes.map(convertResourceRefToCoordinator)
      }.toSeq
    )
  }

  private def convertResourceRefToCoordinator(nodeResourceRef: NodeResourceRef): CoordinatorPlan.NodeResourceRef = {
    val (_, resource) = job.graph.resolveReference(nodeResourceRef).getOrElse {
      throw new IllegalStateException(s"Could not resolve node reference ${nodeResourceRef}")
    }
    CoordinatorPlan.NodeResourceRef(
      node = nodeResourceRef.node,
      resource = nodeResourceRef.resource,
      contentType = resource.contentType
    )
  }

  def convertCoordinator: skuber.batch.Job = {
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
                  KubernetesConstants.RoleName -> KubernetesConstants.CoordinatorRole
                )
              ),
              spec = Some(
                Pod.Spec(
                  containers = List(
                    Container(
                      name = KubernetesConstants.CoordinatorContainerName,
                      image = config.coordinator.image,
                      args = (config.coordinator.parameters ++ Seq("-plan", "@/config/plan")).toList,
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
                      ),
                      imagePullPolicy = createImagePullPolicy(config.coordinator)
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
                  restartPolicy = RestartPolicy.Never,
                  imagePullSecrets = pullSecret.toList.map { secret =>
                    LocalObjectReference(secret.name)
                  }
                )
              )
            )
          )
        )
      )
    )
  }
}
