package ai.mantik.executor.kubernetes

import java.nio.charset.StandardCharsets

import ai.mantik.executor.common.PayloadProvider
import ai.mantik.executor.model.docker.DockerLogin
import ai.mantik.executor.model.{ ContainerService, DataProvider, ExistingService, NodeService }
import io.circe.Json
import skuber.{ Container, LocalObjectReference, ObjectMeta, Pod, PodSecurityContext, RestartPolicy, Secret, Volume }

/** Converts Graph objects into Kubernetes definitions. */
private[kubernetes] class KubernetesConverter(
    config: Config,
    val id: String,
    val extraLogins: Seq[DockerLogin],
    val superPrefix: String,
    val idLabel: String
) {

  val namer = new KubernetesNamer(id, superPrefix)

  protected def defaultLabels = Map(
    idLabel -> KubernetesNamer.encodeLabelValue(id),
    KubernetesConstants.TrackerIdLabel -> KubernetesNamer.encodeLabelValue(config.podTrackerId),
    KubernetesConstants.ManagedLabel -> KubernetesNamer.encodeLabelValue(KubernetesConstants.ManagedValue)
  )

  /** Returns docker secrets for getting images, if needed. */
  lazy val pullSecret: Option[Secret] = {
    val allLogins = (config.dockerConfig.logins ++ extraLogins).distinct
    if (allLogins.isEmpty) {
      None
    } else {
      // Doc: https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/
      val dockerConfigFile = Json.obj(
        "auths" -> Json.obj(
          allLogins.map { login =>
            login.repository -> Json.obj(
              "username" -> Json.fromString(login.username),
              "password" -> Json.fromString(login.password)
            )
          }: _*
        )
      )
      Some(
        Secret(
          metadata = ObjectMeta(
            name = namer.pullSecretName,
            labels = defaultLabels
          ),
          data = Map(
            ".dockerconfigjson" -> dockerConfigFile.spaces2.getBytes(StandardCharsets.UTF_8)
          ),
          `type` = "kubernetes.io/dockerconfigjson"
        )
      )
    }
  }

  def convertNode(nodeName: String, nodeService: NodeService): Pod = {
    Pod(
      metadata = ObjectMeta(
        name = namer.podName(nodeName),
        labels = defaultLabels ++ Map(
          KubernetesConstants.RoleName -> KubernetesConstants.WorkerRole
        )
      ),
      spec = Some(convertNodeSpec(nodeService, withSideCar = true))
    )
  }

  def convertNodeSpec(nodeService: NodeService, withSideCar: Boolean): Pod.Spec = {
    val maybeSideCar = if (withSideCar) {
      Some(createSidecar(nodeService))
    } else {
      None
    }

    // TODO: IoAffinity!

    val maybeMain = nodeService match {
      case ct: ContainerService =>
        val resolved = config.dockerConfig.resolveContainer(ct.main)
        Some(Container(
          name = "main",
          image = resolved.image,
          imagePullPolicy = createImagePullPolicy(ct.main),
          args = resolved.parameters.toList,
          volumeMounts = ct.dataProvider.map { dataProvider =>
            List(Volume.Mount(name = "data", mountPath = "/data"))
          }.getOrElse(Nil)
        ))
      case et: ExistingService =>
        None
    }

    val maybePayloadPreparer = nodeService match {
      case ct: ContainerService => ct.dataProvider.map(createPayloadPreparer)
      case _                    => None
    }

    val dataVolume = nodeService match {
      case ct: ContainerService => Some(Volume("data", Volume.EmptyDir()))
      case _                    => None
    }

    val spec = Pod.Spec(
      containers = maybeSideCar.toList ++ maybeMain.toList,
      initContainers = maybePayloadPreparer.toList,
      restartPolicy = RestartPolicy.Never,
      securityContext = Some(
        // Important, so that the container can write into the volumes created by the payload unpacker
        PodSecurityContext(fsGroup = Some(1000))
      ),
      volumes = dataVolume.toList,
      imagePullSecrets = pullSecret.toList.map { secret =>
        LocalObjectReference(secret.name)
      }
    )
    spec
  }

  private def createSidecar(nodeService: NodeService): Container = {
    // TODO: SideCars listen per default on Port 8503
    // But we have no mechanism to prevent clashers, if the service listens on the same port

    val shutdownParameters = nodeService match {
      case e: ExistingService => Nil
      case _                  => List("-shutdown") // shutdown the http server after quitting the sidecar.
    }

    Container(
      name = KubernetesConstants.SidecarContainerName,
      image = config.common.sideCar.image,
      args = config.common.sideCar.parameters.toList ++ List("-url", urlForSideCar(nodeService)) ++ shutdownParameters,
      imagePullPolicy = createImagePullPolicy(config.common.sideCar)
    )
  }

  private def urlForSideCar(nodeService: NodeService): String = {
    nodeService match {
      case ExistingService(url) => url
      case c: ContainerService  => s"http://localhost:${c.port}"
    }
  }

  /** Creates the container definition of the payload_preparer. */
  private def createPayloadPreparer(dataProvider: DataProvider): Container = {
    val extraArguments = PayloadProvider.createExtraArguments(
      dataProvider
    )

    Container(
      name = "data-provider",
      image = config.common.payloadPreparer.image,
      args = config.common.payloadPreparer.parameters.toList ++ extraArguments,
      volumeMounts = List(
        Volume.Mount(name = "data", mountPath = "/data")
      ),
      imagePullPolicy = createImagePullPolicy(config.common.payloadPreparer)
    )
  }

  def createImagePullPolicy(container: ai.mantik.executor.model.docker.Container): Container.PullPolicy.Value = {
    KubernetesConverter.createImagePullPolicy(config.common.disablePull, container)
  }
}

object KubernetesConverter {
  def createImagePullPolicy(disablePull: Boolean, container: ai.mantik.executor.model.docker.Container): Container.PullPolicy.Value = {
    if (disablePull) {
      return Container.PullPolicy.Never
    }
    // Overriding the policy to a similar behaviour to kubernetes default
    container.imageTag match {
      case None           => Container.PullPolicy.Always
      case Some("latest") => Container.PullPolicy.Always
      case _              => Container.PullPolicy.IfNotPresent
    }
  }
}
