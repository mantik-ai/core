package ai.mantik.executor.impl.tracker

import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.scalalogging.Logger
import ai.mantik.executor.Config
import ai.mantik.executor.impl.{ K8sOperations, KubernetesJobConverter }
import ai.mantik.executor.model.JobState
import skuber.api.client.KubernetesClient
import skuber.batch.Job

import scala.collection.mutable
import scala.concurrent.ExecutionContext

/** Tracks the state of Jobs. */
class KubernetesTracker(config: Config, ops: K8sOperations)(implicit ec: ExecutionContext, actorSystem: ActorSystem) {
  import KubernetesTracker._

  private val jobTrackers = mutable.Map.empty[Key, ActorRef]
  private object lock

  val logger = Logger(getClass)

  def subscribe(key: Key): Unit = {
    ops.getJob(Some(key.namespace), key.jobId).foreach {
      case None =>
        logger.warn(s"Could not find job ${key}")
      case Some(job) =>
        subscribe(key, job)
    }
  }

  def subscribe(job: Job): Unit = {
    val jobId = job.metadata.labels.get(KubernetesJobConverter.JobIdLabel)
    jobId match {
      case None =>
        logger.warn(s"Cannot add job ${job.namespace}/${job.name}, no jobId label")
      case Some(jobId) =>
        subscribe(Key(job.namespace, jobId), job)
    }
  }

  def subscribe(key: Key, job: Job): Unit = {
    lock.synchronized {
      if (jobTrackers.contains(key)) {
        logger.info(s"Job ${key} already tracked")
      } else {
        val tracker = actorSystem.actorOf(JobTracker.props(ops))
        tracker ! JobTracker.Start(job)
        jobTrackers(key) = tracker
      }
    }
  }

  def addAll(): Unit = {
    ops.getManagedNonFinishedJobsForAllNamespaces().foreach { jobs =>
      jobs.foreach(subscribe)
    }
  }

  def shutdown(): Unit = {
    lock.synchronized {
      jobTrackers.foreach { case (key, tracker) => tracker ! JobTracker.Stop }
      jobTrackers.clear()
    }
  }

  addAll()
}

object KubernetesTracker {

  case class Add(key: Key)

  case class Key(
      namespace: String,
      jobId: String
  )

  case class Status(
      state: JobState
  )
}