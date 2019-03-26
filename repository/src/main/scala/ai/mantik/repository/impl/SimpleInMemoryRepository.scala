package ai.mantik.repository.impl

import ai.mantik.repository.{ MantikArtefact, MantikId, Repository }
import ai.mantik.repository.Errors

import scala.collection.mutable
import scala.concurrent.Future

class SimpleInMemoryRepository extends Repository {

  object lock
  val artefacts = mutable.Map.empty[MantikId, MantikArtefact]

  override def get(id: MantikId): Future[MantikArtefact] = {
    lock.synchronized {
      artefacts.get(id) match {
        case Some(a) => Future.successful(a)
        case None    => Future.failed(new Errors.NotFoundException(s"Item ${id} not found"))
      }
    }
  }

  override def store(mantikArtefact: MantikArtefact): Future[Unit] = {
    lock.synchronized {
      Future.successful(
        artefacts.put(mantikArtefact.id, mantikArtefact)
      )
    }
  }

}
