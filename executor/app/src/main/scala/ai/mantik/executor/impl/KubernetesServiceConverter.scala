package ai.mantik.executor.impl

import ai.mantik.executor.Config
import ai.mantik.executor.model.DeployServiceRequest
import skuber.{LabelSelector, ObjectMeta, Pod, RestartPolicy, Service}
import skuber.apps.v1.ReplicaSet

/** Converts Service Deployments */
class KubernetesServiceConverter(
  config: Config,
  deployServiceRequest: DeployServiceRequest,
) extends KubernetesConverter (
  config, deployServiceRequest.serviceId, deployServiceRequest.extraLogins, "service-", KubernetesConstants.ServiceIdLabel
){

  /** The kubernetes Replica Set definition. */
  lazy val replicaSet: ReplicaSet = {
    ReplicaSet (
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
        labels = defaultLabels ++ Map (
          KubernetesConstants.RoleName -> KubernetesConstants.WorkerRole
        )
      ),
      spec = Some(podSpec)
    )
  }

  /** The Kubernetes service definition. */
  lazy val service: Service  = {
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
              targetPort = Some(Left(deployServiceRequest.nodeService.port))
            )
          )
        )
      )
    )
  }

  lazy val podSpec: Pod.Spec = {
    convertNodeSpec(deployServiceRequest.nodeService, withSideCar = false)
      .copy(
        restartPolicy = RestartPolicy.Always // only supported value within ReplicaSet
      )
  }

}
