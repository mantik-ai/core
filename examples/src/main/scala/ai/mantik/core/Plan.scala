package ai.mantik.core

import ai.mantik.ds.DataType
import ai.mantik.ds.element.Bundle
import ai.mantik.executor.model.Job
import ai.mantik.repository.{ MantikArtefact, MantikDefinition }
import akka.stream.scaladsl.{ Source => AkkaSource }
import akka.util.ByteString

/** Define something which can be executed. */
sealed trait Plan

object Plan {
  /** Nothing to do. */
  case object Empty extends Plan

  /** Run a job. */
  case class RunJob(job: Job) extends Plan

  /** Push a bundles content to the file repository. */
  case class PushBundle(bundle: Bundle, fileId: String) extends Plan

  /** Pulls a bundle from the file repository. */
  case class PullBundle(dataType: DataType, fileId: String) extends Plan

  /** Add some mantik item. */
  case class AddMantikItem(artefact: MantikArtefact) extends Plan

  /**
   * Run something sequentially, waiting for each other.
   * The result of the last is returned.
   */
  case class Sequential(plans: Seq[Plan]) extends Plan

  /** Convenience method for constructing Sequential Plans. */
  def seq(plans: Plan*): Sequential = Sequential(plans)

  /** Combine two plans so that they are executed afterwards, compressing on the fly. */
  def combine(plan1: Plan, plan2: Plan): Plan = {
    plan1 match {
      case Plan.Empty               => plan2
      case x if plan2 == Plan.Empty => x
      case Plan.Sequential(elements) =>
        plan2 match {
          case Plan.Sequential(nextElements) =>
            Plan.Sequential(elements ++ nextElements)
          case other =>
            Plan.Sequential(elements :+ other)
        }
      case x =>
        plan2 match {
          case Plan.Sequential(nextElements) =>
            Plan.Sequential(x +: nextElements)
          case other =>
            Plan.seq(x, other)
        }
    }
  }
}
