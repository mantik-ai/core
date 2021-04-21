package ai.mantik.planner.impl.exec

import ai.mantik.componently.utils.FutureHelper
import ai.mantik.ds.element.Bundle
import ai.mantik.planner.PlanOp
import ai.mantik.planner.Planner.InconsistencyException
import ai.mantik.planner.impl.MantikItemStateManager
import ai.mantik.planner.repository.{ContentTypes, FileRepository, MantikArtifact, MantikArtifactRetriever, Repository}
import akka.stream.Materializer
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

class BasicOpExecutor(
    fileRepository: FileRepository,
    repository: Repository,
    artifactRetriever: MantikArtifactRetriever,
    mantikItemStateManager: MantikItemStateManager
)(implicit ec: ExecutionContext, mat: Materializer) {
  val logger = Logger(getClass)

  def execute[T](planOp: PlanOp.BasicOp[T])(implicit files: ExecutionOpenFiles, memory: Memory): Future[T] = {
    planOp match {
      case PlanOp.Empty =>
        logger.debug(s"Executing empty")
        Future.successful(())
      case PlanOp.StoreBundleToFile(bundle, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Push $fileId") {
          fileRepository.storeFile(fileId).flatMap { sink =>
            val source = bundle.encode(withHeader = true)
            source.runWith(sink).map(_ => ())
          }
        }
      case PlanOp.LoadBundleFromFile(_, fileRef) =>
        val fileId = files.resolveFileId(fileRef)
        FutureHelper.time(logger, s"Bundle Pull $fileId") {
          fileRepository.loadFile(fileId).flatMap { result =>
            val sink = Bundle.fromStreamWithHeader()
            result.source.runWith(sink)
          }
        }
      case PlanOp.AddMantikItem(item, fileReference) =>
        val fileId = fileReference.map(files.resolveFileId)
        val mantikHeader = item.mantikHeader
        val id = item.itemId
        val state = mantikItemStateManager.getOrInit(item)
        val namedId = state.namedMantikItem
        val artifact = MantikArtifact(mantikHeader.toJson, fileId, namedId, item.itemId)
        FutureHelper.time(logger, s"Adding Mantik Item $id") {
          repository.store(artifact).andThen { case Success(_) =>
            mantikItemStateManager.update(
              id,
              _.copy(
                itemStored = true,
                nameStored = namedId.isDefined,
                namedMantikItem = namedId,
                payloadFile = fileId
              )
            )
          }
        }
      case PlanOp.TagMantikItem(item, id) =>
        FutureHelper.time(logger, s"Tagging Mantik Item") {
          repository
            .ensureMantikId(item.itemId, id)
            .andThen { case Success(_) =>
              mantikItemStateManager.update(
                item.itemId,
                _.copy(
                  namedMantikItem = Some(id)
                )
              )
            }
            .map(_ => ())
        }
      case PlanOp.PushMantikItem(item) =>
        val state = mantikItemStateManager.getOrInit(item)
        if (!state.itemStored) {
          throw new InconsistencyException("Item is not stored")
        }
        val mantikId = item.mantikId
        FutureHelper
          .time(logger, s"Pushing Artifact ${mantikId}") {
            artifactRetriever.push(mantikId)
          }
          .map { _ => () }
      case cacheOp: PlanOp.MarkCached =>
        cacheOp.files.foreach { case (itemId, fileRef) =>
          val resolved = files.resolveFileId(fileRef)
          mantikItemStateManager.updateOrFresh(itemId, _.copy(cacheFile = Some(resolved)))
          ()
        }
        Future.successful(())
      case c: PlanOp.Const[T] =>
        Future.successful(c.value)
      case c: PlanOp.CopyFile =>
        val fromId = files.resolveFileId(c.from)
        val toId = files.resolveFileId(c.to)
        FutureHelper.time(logger, "Copy file") {
          fileRepository.copy(fromId, toId)
        }
      case c: PlanOp.MemoryReader[T] =>
        Future.successful(memory.get(c.memoryId).asInstanceOf[T])
      case c: PlanOp.MemoryWriter[T] =>
        val last = memory.getLast().asInstanceOf[T]
        memory.put(c.memoryId, last)
        Future.successful(last)
    }
  }

}
