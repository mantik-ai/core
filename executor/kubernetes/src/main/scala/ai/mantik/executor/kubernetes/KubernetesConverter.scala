package ai.mantik.executor.kubernetes

import java.nio.charset.StandardCharsets
import java.util.Base64
import ai.mantik.executor.common.LabelConstants
import ai.mantik.executor.model.{ MnpPipelineDefinition, MnpWorkerDefinition, StartWorkerRequest }
import ai.mantik.executor.model.docker.DockerLogin
import io.circe.Json
import skuber.apps.v1.Deployment
import skuber.ext.Ingress
import skuber.{ Container, EnvVar, LabelSelector, ObjectMeta, Pod, Secret, Service }

/** Converts requests into Kubernetes Structures */
case class KubernetesConverter(
    config: Config,
    kubernetesHost: String
) {
  def convertStartWorkRequest(internalId: String, startWorkerRequest: StartWorkerRequest): Workload = {
    val nameHintPrefix = startWorkerRequest.nameHint.map { nameHint =>
      KubernetesNamer.escapeNodeName(nameHint) + "-"
    }.getOrElse("")

    startWorkerRequest.definition match {
      case wd: MnpWorkerDefinition   => createMnpWorker(internalId, nameHintPrefix, startWorkerRequest, wd)
      case pd: MnpPipelineDefinition => createMnpPipeline(internalId, nameHintPrefix, startWorkerRequest, pd)
    }
  }

  private def createMnpWorker(
    internalId: String,
    nameHintPrefix: String,
    startWorkerRequest: StartWorkerRequest,
    definition: MnpWorkerDefinition
  ): Workload = {

    val mainLabels = Map(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.RoleLabelName -> LabelConstants.role.worker,
      LabelConstants.UserIdLabelName -> KubernetesNamer.encodeLabelValue(startWorkerRequest.id),
      LabelConstants.InternalIdLabelName -> internalId,
      LabelConstants.WorkerTypeLabelName -> LabelConstants.workerType.mnpWorker
    )

    val workLoad: Either[Pod, Deployment] = if (startWorkerRequest.keepRunning) {
      Right(generateWorkerDeployment(mainLabels, nameHintPrefix, definition))
    } else {
      Left(generateWorkerPod(mainLabels, nameHintPrefix, definition))
    }

    val service = generateWorkerService(internalId, mainLabels, nameHintPrefix)
    val ingress = startWorkerRequest.ingressName.map { ingressName =>
      createPlainIngress(service, ingressName)
    }

    Workload(
      internalId = internalId,
      pod = workLoad.left.toOption,
      deployment = workLoad.right.toOption,
      service,
      ingress = ingress
    )
  }

  private def generateWorkerPod(
    mainLabels: Map[String, String],
    nameHintPrefix: String,
    definition: MnpWorkerDefinition
  ): Pod = {
    val podSpec = generateMnpWorkerPodSpec(definition)
    Pod(
      metadata = ObjectMeta(
        labels = mainLabels,
        generateName = nameHintPrefix + "worker"
      ),
      spec = Some(
        podSpec
      )
    )
  }

  private def generateWorkerDeployment(
    mainLabels: Map[String, String],
    nameHintPrefix: String,
    definition: MnpWorkerDefinition
  ): Deployment = {
    val id = mainLabels(LabelConstants.InternalIdLabelName)
    Deployment(
      metadata = ObjectMeta(
        labels = mainLabels,
        generateName = nameHintPrefix + "deployment"
      ),
      spec = Some(
        Deployment.Spec(
          selector = LabelSelector(
            LabelSelector.IsEqualRequirement(LabelConstants.ManagedByLabelName, LabelConstants.ManagedByLabelValue),
            LabelSelector.IsEqualRequirement(LabelConstants.InternalIdLabelName, id)
          ),
          template =
            Pod.Template.Spec(
              metadata = ObjectMeta(
                labels = mainLabels,
                generateName = nameHintPrefix + "worker"
              ),
              spec = Some(generateMnpWorkerPodSpec(definition))
            )
        )
      )
    )
  }

  private def generateMnpWorkerPodSpec(
    definition: MnpWorkerDefinition
  ): skuber.Pod.Spec = {
    val workerContainer = generateMnpWorkerContainer(definition)
    val maybePreparer = generateMnpPreparer(definition)
    Pod.Spec(
      containers = List(workerContainer) ++ maybePreparer
    )
  }

  private def generateMnpWorkerContainer(
    definition: MnpWorkerDefinition
  ): skuber.Container = {
    val resolvedContainer = config.dockerConfig.resolveContainer(definition.container)
    skuber.Container(
      name = "worker",
      image = resolvedContainer.image,
      args = resolvedContainer.parameters.toList,
      imagePullPolicy = KubernetesConverter.createImagePullPolicy(config.common.disablePull, resolvedContainer)
    )
  }

  private def generateMnpPreparer(
    definition: MnpWorkerDefinition
  ): Option[skuber.Container] = {
    definition.initializer.map { initRequest =>
      val mainAddress = s"localhost:8502"
      val parameters = Seq(
        "--address", mainAddress, "--keepRunning", "true"
      )
      val allParameters = config.common.mnpPreparer.parameters ++ parameters
      val encodedInitRequest = Base64.getEncoder.encodeToString(initRequest.toArray[Byte])

      skuber.Container(
        name = "initializer",
        image = config.common.mnpPreparer.image,
        args = allParameters.toList,
        env = List(
          EnvVar("MNP_INIT", encodedInitRequest)
        ),
        imagePullPolicy = KubernetesConverter.createImagePullPolicy(config.common.disablePull, config.common.mnpPreparer)
      )
    }
  }

  private def generateWorkerService(
    internalId: String,
    mainLabels: Map[String, String],
    nameHintPrefix: String
  ): Service = {
    Service(
      metadata = ObjectMeta(
        labels = mainLabels,
        generateName = nameHintPrefix + "worker"
      ),
      spec = Some(
        Service.Spec(
          selector = Map(
            LabelConstants.InternalIdLabelName -> internalId
          ),
          ports = List(
            Service.Port(
              port = 8502
            )
          )
        )
      )
    )
  }

  def createMnpPipeline(
    internalId: String,
    nameHintPrefix: String,
    request: StartWorkerRequest,
    definition: MnpPipelineDefinition
  ): Workload = {

    val mainLabels = Map(
      LabelConstants.ManagedByLabelName -> LabelConstants.ManagedByLabelValue,
      LabelConstants.RoleLabelName -> LabelConstants.workerType.mnpPipeline,
      LabelConstants.UserIdLabelName -> KubernetesNamer.encodeLabelValue(request.id),
      LabelConstants.InternalIdLabelName -> internalId,
      LabelConstants.WorkerTypeLabelName -> LabelConstants.workerType.mnpPipeline
    )

    val container = generatePipelineContainer(definition)

    val podSpec = Pod.Spec(
      containers = List(
        container
      )
    )

    val deploymentOrPod: Either[Deployment, Pod] = if (request.keepRunning) {
      Left(
        Deployment(
          metadata = ObjectMeta(
            generateName = nameHintPrefix + "deployment",
            labels = mainLabels
          ),
          spec = Some(
            Deployment.Spec(
              selector = LabelSelector(
                LabelSelector.IsEqualRequirement(LabelConstants.ManagedByLabelName, LabelConstants.ManagedByLabelValue),
                LabelSelector.IsEqualRequirement(LabelConstants.InternalIdLabelName, internalId)
              ),
              template =
                Pod.Template.Spec(
                  metadata = ObjectMeta(
                    generateName = nameHintPrefix + "pipeline",
                    labels = mainLabels
                  ),
                  spec = Some(podSpec)
                )
            )
          )
        )
      )
    } else {
      Right(
        Pod(
          metadata = ObjectMeta(
            generateName = nameHintPrefix + "pipeline",
            labels = mainLabels
          ),
          spec = Some(podSpec)
        )
      )
    }

    val service = generateWorkerService(internalId, mainLabels, nameHintPrefix)
    val ingress = request.ingressName.map { ingressName =>
      createPlainIngress(service, ingressName)
    }

    Workload(
      internalId = internalId,
      pod = deploymentOrPod.right.toOption,
      deployment = deploymentOrPod.left.toOption,
      service = service,
      ingress = ingress
    )
  }

  private def generatePipelineContainer(definition: MnpPipelineDefinition): skuber.Container = {
    val container = config.common.mnpPipelineController
    val pipelineValue = definition.definition.noSpaces
    val extraArgs = Vector("-port", "8502")
    val allParameters = container.parameters ++ extraArgs
    skuber.Container(
      name = "pipeline-controller",
      image = container.image,
      args = allParameters.toList,
      env = List(
        EnvVar("PIPELINE", pipelineValue)
      ),
      imagePullPolicy = KubernetesConverter.createImagePullPolicy(config.common.disablePull, container)
    )
  }

  /** Create an ingress. Note: the service name may be empty still if it's not defined within the service */
  private def createPlainIngress(service: Service, ingressName: String): Ingress = {
    val annotations = config.kubernetes.ingressAnnotations.mapValues { annotation =>
      interpolateIngressString(annotation, ingressName)
    }

    val servicePort = (for {
      spec <- service.spec
      firstPort <- spec.ports.headOption
    } yield firstPort.port).getOrElse(
      throw new IllegalArgumentException("Could not find out port for service")
    )

    Ingress(
      metadata = ObjectMeta(
        name = ingressName,
        labels = service.metadata.labels,
        annotations = annotations
      ),
      spec = Some(
        ingressSpec(service.name, ingressName, servicePort)
      )
    )
  }

  private def ingressSpec(serviceName: String, ingressName: String, servicePort: Int): Ingress.Spec = {
    val backend =
      Ingress.Backend(
        serviceName = serviceName,
        servicePort = servicePort
      )

    config.kubernetes.ingressSubPath match {
      case Some(subPath) =>
        // Allocating sub path
        val path = interpolateIngressString(subPath, ingressName)
        Ingress.Spec(
          rules = List(
            Ingress.Rule(
              host = None,
              http = Ingress.HttpRule(
                paths = List(
                  Ingress.Path(
                    path = path,
                    backend = backend
                  )
                )
              )
            )
          )
        )
      case None =>
        // Ingress with direct service
        Ingress.Spec(
          backend = Some(
            backend
          )
        )
    }
  }

  private def interpolateIngressString(in: String, ingressName: String): String = {
    in
      .replace("${name}", ingressName)
      .replace("${kubernetesHost}", kubernetesHost)
  }
}

object KubernetesConverter {

  /** Returns docker secrets for getting images, if needed. */
  def pullSecret(config: Config, extraLogins: Seq[DockerLogin]): Option[Secret] = {
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
          metadata = ObjectMeta(),
          data = Map(
            ".dockerconfigjson" -> dockerConfigFile.spaces2.getBytes(StandardCharsets.UTF_8)
          ),
          `type` = "kubernetes.io/dockerconfigjson"
        )
      )
    }
  }

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
