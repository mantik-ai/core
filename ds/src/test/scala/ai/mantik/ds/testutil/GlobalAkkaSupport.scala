package ai.mantik.ds.testutil

/** Adds akka support to a testcase. */
// Compatibility trait to reduce the number of changes. Remove Me.
trait GlobalAkkaSupport extends ai.mantik.testutils.AkkaSupport {
  self: TestBase =>
}
