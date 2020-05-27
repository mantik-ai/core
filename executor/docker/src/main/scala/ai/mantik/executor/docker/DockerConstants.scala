package ai.mantik.executor.docker

object DockerConstants {
  /** Name of the label describing the isolation space */
  val IsolationSpaceLabelName = "ai.mantik.isolationSpace"

  /** Name of the label containing the (internal) job id. */
  val IdLabelName = "ai.mantik.id"

  /** Name of a user provided id labe. */
  val UserIdLabelName = "ai.mantik.userId"

  val TypeLabelName = "ai.mantik.type"
  val JobType = "job"
  val ServiceType = "service"
  // New MNP Worker Type
  val WorkerType = "worker"

  /** Main Listening port (e.g. for services) */
  val PortLabel = "ai.mantik.port"

  /** Label for manged by (copied from kubernetes implementation). */
  val ManagedByLabelName = "app.kubernetes.io/managed-by"
  val ManagedByLabelValue = "mantik"

  /** Name of the label defining the role */
  val RoleLabelName = "ai.mantik.role"

  val WorkerRole = "worker"
  val CoordinatorRole = "coordinator"
  val PayloadProviderRole = "payloadprovider"
  val PipelineRole = "pipeline"

  /** Extra group used for workers and payload providers. */
  val WorkerPayloadGroup = "1000"

  /** Suffix used to detect internal services. */
  val InternalServiceNameSuffix = ".internal"
}
