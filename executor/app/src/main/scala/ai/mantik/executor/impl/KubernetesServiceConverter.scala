package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model.{ DeployServiceRequest, DeployableService }
import skuber.{ Container, EnvVar, LabelSelector, LocalObjectReference, ObjectMeta, Pod, PodSecurityContext, RestartPolicy, Service, Volume }
import skuber.apps.v1.ReplicaSet
import skuber.ext.Ingress

/**
 * Converts Service Deployments
 *
 * @param kubernetesHost the Host of Kubernetes (for interpolating ingress).
 */
class KubernetesServiceConverter(
    config: Config,
    deployServiceRequest: DeployServiceRequest,
    kubernetesHost: String
) extends KubernetesConverter(
  config, deployServiceRequest.serviceId, deployServiceRequest.extraLogins, "service-", KubernetesConstants.ServiceIdLabel
) {

  /** The kubernetes Replica Set definition. */
  lazy val replicaSet: ReplicaSet = {
    ReplicaSet(
      metadata = ObjectMeta(
        name = namer.replicaSetName,
        labels = defaultLabels
      ),
      spec = Some(ReplicaSet.Spec(
        selector = LabelSelector(
          LabelSelector.IsEqualRequirement(
            KubernetesConstants.ServiceIdLabel, KubernetesNamer.encodeLabelValue(id)
          )
        ),
        template = podTemplateSpec
      ))
    )
  }

  lazy val podTemplateSpec: Pod.Template.Spec = {
    Pod.Template.Spec(
      metadata = ObjectMeta(
        labels = defaultLabels ++ Map(
          KubernetesConstants.RoleName -> KubernetesConstants.WorkerRole
        )
      ),
      spec = Some(podSpec)
    )
  }

  /** The Kubernetes service definition. */
  lazy val service: Service = {
    Service(
      metadata = ObjectMeta(
        name = if (deployServiceRequest.nameHint.isDefined) {
          ""
        } else {
          namer.serviceName
        },
        generateName = deployServiceRequest.nameHint.getOrElse(""),
        labels = defaultLabels
      ),
      spec = Some(
        Service.Spec(
          selector = Map(
            KubernetesConstants.ServiceIdLabel -> KubernetesNamer.encodeLabelValue(id)
          ),
          ports = List(
            Service.Port(
              port = 80,
              targetPort = Some(Left(servicePort))
            )
          )
        )
      )
    )
  }

  /**
   * Optional ingress definition, if required.
   * @param serviceName name of the deployed service.
   */
  def ingress(serviceName: String): Option[Ingress] = {
    val annotations = config.kubernetes.ingressAnnotations.mapValues(interpolateIngressString)

    deployServiceRequest.ingress.map { ingressName =>
      Ingress(
        metadata = ObjectMeta(
          name = ingressName,
          labels = defaultLabels,
          annotations = annotations
        ),
        spec = Some(
          ingressSpec(serviceName, ingressName)
        )
      )
    }
  }

  def ingressExternalUrl: Option[String] = {
    deployServiceRequest.ingress.map { _ =>
      interpolateIngressString(config.kubernetes.ingressRemoteUrl)
    }
  }

  private def ingressSpec(serviceName: String, ingressName: String): Ingress.Spec = {
    val backend =
      Ingress.Backend(
        serviceName = serviceName,
        servicePort = 80
      )

    config.kubernetes.ingressSubPath match {
      case Some(subPath) =>
        // Allocating sub path
        val path = interpolateIngressString(subPath)
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

  private def interpolateIngressString(in: String): String = {
    val ingressName = deployServiceRequest.ingress.getOrElse("")
    in
      .replace("${name}", ingressName)
      .replace("${kubernetesHost}", kubernetesHost)
  }

  lazy val podSpec: Pod.Spec = {
    deployServiceRequest.service match {
      case DeployableService.SingleService(service) =>
        convertNodeSpec(service, withSideCar = false)
          .copy(
            restartPolicy = RestartPolicy.Always // only supported value within ReplicaSet
          )
      case p: DeployableService.Pipeline =>
        convertPipelinePodSpec(p)
    }
  }

  private def convertPipelinePodSpec(pipeline: DeployableService.Pipeline): Pod.Spec = {
    val resolved = config.pipelineController
    val args = resolved.parameters.toList ++ List(
      "-port", pipeline.port.toString
    )
    val pipelineDef = pipeline.pipeline.toString()
    val controller = Container(
      name = "controller",
      image = resolved.image,
      imagePullPolicy = createImagePullPolicy(resolved),
      args = args,
      env = List(
        EnvVar(
          "PIPELINE", EnvVar.StringValue(pipelineDef)
        )
      )
    )
    val spec = Pod.Spec(
      containers = List(controller),
      restartPolicy = RestartPolicy.Always,
      volumes = Nil,
      imagePullSecrets = pullSecret.toList.map { secret =>
        LocalObjectReference(secret.name)
      }
    )

    spec
  }

  lazy val servicePort: Int = {
    deployServiceRequest.service match {
      case DeployableService.SingleService(service) => service.port
      case p: DeployableService.Pipeline            => p.port
    }
  }

}
