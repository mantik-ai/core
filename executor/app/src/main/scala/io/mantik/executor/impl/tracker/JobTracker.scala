package io.mantik.executor.impl.tracker

import akka.actor.{ Actor, Props }
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.Logger
import io.mantik.executor.model.JobState
import skuber.api.client.{ KubernetesClient, WatchEvent }
import skuber.batch.Job
import skuber.json.batch.format._

import scala.concurrent.ExecutionContext

/** Tracks the state of a single job. */
class JobTracker(client: KubernetesClient) extends Actor {
  import JobTracker._

  var state: JobState = JobState.Pending
  val logger = Logger(getClass)

  implicit val materializer = ActorMaterializer()
  implicit def ec: ExecutionContext = context.dispatcher

  override def receive: Receive = {
    case s: Start =>
      logger.info(s"Start monitoring ${s.job.namespace}/${s.job.name}")
      client.watch(s.job).foreach { result =>
        result.runWith(Sink.actorRef(self, OnQuitEvent))
      }
    case w: WatchEvent[Job] =>
      logger.debug("Watch Event", w._object)
    case Stop =>
      logger.warn("Quit")
    case q: OnQuitEvent =>
      logger.info("Object Quit Event")
  }
}

object JobTracker {

  def props(client: KubernetesClient): Props = {
    Props.apply(new JobTracker(client))
  }

  case class Start(job: Job)
  case object Stop

  // Internal
  case class OnQuitEvent()
}
