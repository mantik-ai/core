package ai.mantik.executor.common

/** Constants for Labels on Containers/Kubernetes Items. */
object LabelConstants {

  /** Name of the label containing the (internal) job id. */
  val InternalIdLabelName = "ai.mantik.internalId"

  /** Name of a user provided id label. */
  val UserIdLabelName = "ai.mantik.userId"

  /** Label for manged by */
  val ManagedByLabelName = "app.kubernetes.io/managed-by"
  val ManagedByLabelValue = "mantik"

  /** Name of the label defining the role */
  val RoleLabelName = "ai.mantik.role"

  object role {

    /** Regular Worker */
    val worker = "worker"

    /** The Ingress Traefik Resource */
    val traefik = "traefik"

    /** Grpc Proxy Role */
    val grpcProxy = "grpcproxy"
  }

  /** Which kind of Worker */
  val WorkerTypeLabelName = "ai.mantik.worker.type"

  object workerType {

    /** An MNP Worker */
    val mnpWorker = "mnp-worker"

    /** An MNP Pipeline */
    val mnpPipeline = "mnp-pipeline"
  }
}
