package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import ai.mantik.componently.utils.ConfigExtensions._
import ai.mantik.componently.utils.FutureHelper
import ai.mantik.componently.{AkkaRuntime, ComponentBase}
import ai.mantik.ds.helper.ZipUtils
import ai.mantik.elements.{ItemId, MantikId, Mantikfile, NamedMantikId}
import ai.mantik.planner.impl.ReferencingItemLoader
import ai.mantik.planner.repository._
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import org.apache.commons.io.FileUtils
import cats.implicits._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** Responsible for pulling/pushing Mantik Artifacts from local repository and remote registry. */
@Singleton
private[mantik] class MantikArtifactRetrieverImpl @Inject() (
    localMantikRegistry: LocalMantikRegistry,
    defaultRemoteRegistry: RemoteMantikRegistry,
)(implicit akkaRuntime: AkkaRuntime) extends ComponentBase with MantikArtifactRetriever {

  private val dbLookupTimeout = config.getFiniteDuration("mantik.planner.dbLookupTimeout")
  private val registryTimeout = config.getFiniteDuration("mantik.planner.registryTimeout")
  private val fileTransferTimeout = config.getFiniteDuration("mantik.planner.fileTransferTimeout")

  /** ReferencingItemLoader for MantikArtifacts. */
  private class ReferencingMantikArtifactLoader(loader: MantikId => Future[MantikArtifact]) extends ReferencingItemLoader[MantikId, MantikArtifact](
    loader,
    _.parsedMantikfile.definition.referencedItems
  )

  private val repositoryLoader = new ReferencingMantikArtifactLoader(localRepoGet)

  private def localRepoGet(mantikId: MantikId): Future[MantikArtifact] =
    FutureHelper.addTimeout(localMantikRegistry.get(mantikId), "Loading Mantikfile from local repository", dbLookupTimeout)

  override def pull(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull] = {
    logger.info(s"Pulling ${id}")
    val remoteRegistry = wrapRemoteRegistry(customLoginToken)
    val loader = new ReferencingMantikArtifactLoader(wrapRemoteGet(remoteRegistry))

    for {
      items <- loader.loadWithHull(id)
      local <- pullRemoteItemsToLocal(remoteRegistry, items)
    } yield (
      (local.head -> local.tail)
    )
  }

  private def wrapRemoteGet(registry: MantikRegistry): MantikId => Future[MantikArtifact] = {
    mantikId =>
      FutureHelper.addTimeout(registry.get(mantikId), "Loading Mantikfile from remote Registry", registryTimeout)
  }

  private def wrapRemoteRegistry(customLoginToken: Option[CustomLoginToken]): MantikRegistry = {
    customLoginToken.map { token =>
      defaultRemoteRegistry.withCustomToken(token)
    }.getOrElse {
      defaultRemoteRegistry
    }
  }

  override def get(id: MantikId): Future[MantikArtifactWithHull] = {
    getLocal(id).recoverWith {
      case n: Errors.NotFoundException =>
        logger.info(s"${id} not available locally, pulling...")
        pull(id)
    }
  }

  override def getLocal(id: MantikId): Future[MantikArtifactWithHull] = {
    repositoryLoader.loadWithHull(id).map { items =>
      items.head -> items.tail
    }
  }

  override def push(id: MantikId, customLoginToken: Option[CustomLoginToken] = None): Future[MantikArtifactWithHull] = {
    val remoteRegistry = wrapRemoteRegistry(customLoginToken)
    for {
      items <- repositoryLoader.loadWithHull(id)
      remote <- pushLocalItemsToRemote(remoteRegistry, items)
    } yield {
      remote.head -> remote.tail
    }
  }

  override def addLocalDirectoryToRepository(dir: Path, id: Option[NamedMantikId] = None): Future[MantikArtifact] = {
    logger.info(s"Adding local Directory ${dir} with Mantikfile")
    val file = dir.resolve("Mantikfile")
    val mantikfileContent = FileUtils.readFileToString(file.toFile, StandardCharsets.UTF_8)
    // Parsing
    val mantikfile = Mantikfile.fromYaml(mantikfileContent) match {
      case Left(error) => throw new IllegalArgumentException("Could not parse mantik file", error)
      case Right(ok)   => ok
    }
    val mantikId = id.orElse(mantikfile.header.id)
    val itemId = ItemId.generate()

    val payloadDir = dir.resolve("payload")
    val payloadSource: Option[(String, Source[ByteString, _])] = if (Files.isDirectory(payloadDir)) {
      val tempFile = Files.createTempFile("mantik_context", ".zip")
      ZipUtils.zipDirectory(payloadDir, tempFile)
      val source = FileIO.fromPath(tempFile)
      Some(ContentTypes.ZipFileContentType -> source)
    } else {
      logger.info("No payload directory found, assuming no payload")
      None
    }


    val artifact =  MantikArtifact(mantikfileContent, None, mantikId, itemId)
    val timeout = if (payloadSource.isDefined){
      fileTransferTimeout
    } else {
      dbLookupTimeout
    }
    FutureHelper.addTimeout(
      localMantikRegistry.addMantikArtifact(artifact, payloadSource), "Uploading Artifact", timeout
    ).map { generatedArtifact =>
      logger.info(s"Stored ${artifact.itemId} done, name=${artifact.namedId}, fileId=${artifact.fileId}")
      generatedArtifact
    }
  }

  private def pullRemoteItemsToLocal(remoteRepo: MantikRegistry, remote: Seq[MantikArtifact]): Future[Seq[MantikArtifact]] = {
    Future.sequence(remote.map(pullRemoteItemToLocal(remoteRepo, _)))
  }

  private def pullRemoteItemToLocal(remoteRegistry: MantikRegistry, remote: MantikArtifact): Future[MantikArtifact] = {
    copyItem(
      "Pulling",
      remote,
      remoteRegistry,
      localMantikRegistry,
      fileTransferTimeout,
      dbLookupTimeout
    )
  }

  /** Copy an item to the `to` Registry. */
  private def copyItem(
    operationName: String,
    fromArtifact: MantikArtifact,
    from: MantikRegistry,
    to: MantikRegistry,
    fileTransferTimeout: FiniteDuration,
    changeTimeout: FiniteDuration
  ): Future[MantikArtifact] = {
    logger.debug(s"${operationName} ${fromArtifact.mantikId}")

    val existing = to.maybeGet(fromArtifact.itemId)

    existing.flatMap {
      case Some(existant) =>
        fromArtifact.namedId match {
          case Some(namedId) =>
            logger.info(s"${fromArtifact.itemId} already exists, ensuring id $namedId")
            FutureHelper.addTimeout(
              to.ensureMantikId(existant.itemId, namedId), "Tagging", changeTimeout
            ).map { _ =>
              existant.copy(namedId = Some(namedId))
            }
          case None =>
            logger.info(s"${fromArtifact.itemId} already exists, and is anonymous, no change.")
            Future.successful(existant)
        }
      case None =>
        logger.info(s"${fromArtifact.itemId} doesn't exist yet, copying entirely")
        val timeout = if (fromArtifact.fileId.isDefined) {
          fileTransferTimeout
        } else {
          changeTimeout
        }

        for {
          source <- FutureHelper.addTimeout(fromArtifact.fileId.map(from.getPayload).sequence, "Getting File", timeout)
          localArtifact <- FutureHelper.addTimeout(to.addMantikArtifact(fromArtifact, source), "Storing Artifact", timeout)
        } yield localArtifact
    }
  }

  /**
   * Push multiple local items to remote.
   * Local must be ordered (dependencies last)
   */
  private def pushLocalItemsToRemote(remoteRegistry: MantikRegistry, local: Seq[MantikArtifact]): Future[Seq[MantikArtifact]] = {
    // Remote may check for dependencies
    val reversed = local.reverse
    FutureHelper.afterEachOther(reversed)(pushLocalItemToRemote(remoteRegistry, _)).map(_.reverse)
  }

  /** Push a single local item to remote registry. */
  private def pushLocalItemToRemote(remoteRegistry: MantikRegistry, local: MantikArtifact): Future[MantikArtifact] = {
    copyItem(
      "pushing",
      local,
      localMantikRegistry,
      remoteRegistry,
      fileTransferTimeout,
      registryTimeout
    )
  }
}
