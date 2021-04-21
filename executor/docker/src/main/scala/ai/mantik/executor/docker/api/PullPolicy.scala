package ai.mantik.executor.docker.api

/** Defines if an image will be pulled, similar to kubernetes. */
sealed trait PullPolicy
object PullPolicy {
  case object Never extends PullPolicy
  case object IfNotPresent extends PullPolicy
  case object Always extends PullPolicy
}
