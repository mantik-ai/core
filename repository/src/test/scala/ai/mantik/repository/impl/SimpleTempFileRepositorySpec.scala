package ai.mantik.repository.impl

import org.apache.commons.io.FileUtils

class SimpleTempFileRepositorySpec extends FileRepositorySpecBase {

  override type FileRepoType = SimpleTempFileRepository with NonAsyncFileRepository

  override protected def createRepo: FileRepoType = {
    new SimpleTempFileRepository(config) with NonAsyncFileRepository
  }
}
