package ai.mantik.planner.repository.impl

import ai.mantik.testutils.TempDirSupport
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

class LocalRepositorySpec extends RepositorySpecBase with TempDirSupport {

  override type RepoType = LocalRepository

  override protected def createRepo(): LocalRepository = {
    new LocalRepository(tempDirectory)
  }
}
