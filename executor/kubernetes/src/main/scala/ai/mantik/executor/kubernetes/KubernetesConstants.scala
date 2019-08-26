package ai.mantik.executor.kubernetes

object KubernetesConstants {
  /** Label used to save the job id in Kubernetes Resources. */
  val JobIdLabel = "jobId"

  /** Label used to identify services in Kubernetes Resources. */
  val ServiceIdLabel = "serviceId"

  /** Label used to save the label id in Kubernetes Resources. */
  val TrackerIdLabel = "trackerId"

  /** Label used to mark items which are managed by Mantik. */
  val ManagedLabel = "app.kubernetes.io/managed-by" // see  https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/

  /** Label Value for Managed Items. */
  val ManagedValue = "mantik"

  /** Name of the coordinator container inside a pod. */
  val CoordinatorContainerName = "coordinator"
  /** Name of the sidecar container inside a pod. */
  val SidecarContainerName = "sidecar"

  /** Names the role of pods, value CoordinatorRole or WorkerRole */
  val RoleName = "role"

  val CoordinatorRole = "coordinator"
  val WorkerRole = "worker"

  /** Name to store kill annotations */
  val KillAnnotationName = "killed"
}
