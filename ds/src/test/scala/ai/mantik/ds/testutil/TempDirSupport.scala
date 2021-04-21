package ai.mantik.ds.testutil

import java.nio.file.{Files, Path}

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterEach

/** Provides a temporary directory. */
// Compatibility trait to reduce the number of changes. Remove Me.
trait TempDirSupport extends ai.mantik.testutils.TempDirSupport {
  self: TestBase =>
}
