package ai.mantik.repository.impl

import ai.mantik.repository.impl.LocalRepositoryDb.DbMantikArtifact
import ai.mantik.testutils.{ TempDirSupport, TestBase }

class LocalRepositoryDbSpec extends TestBase with TempDirSupport {

  private def withDb[T](f: LocalRepositoryDb => T): Unit = {
    val file = tempDirectory.resolve("my.db")
    val db = new LocalRepositoryDb(file)
    try {
      f(db)
    } finally {
      db.shutdown()
    }
  }

  val sampleItem = DbMantikArtifact(
    name = "foo",
    version = "version",
    fileId = Some("1"),
    mantikfile = "blabla"
  )

  it should "store elements" in {
    withDb { db =>
      import db.quill.context._
      val items = db.quill.context.run(
        quote {
          db.artifacts
        }
      )
      items shouldBe empty

      db.quill.context.run {
        quote {
          db.artifacts.insert(lift(sampleItem))
        }
      }

      val back = db.quill.context.run(
        quote {
          db.artifacts
        }
      )

      back shouldBe Seq(sampleItem)
    }
  }

  it should "initialize twice" in {
    withDb { db1 =>
      withDb { db2 =>
        {
          import db2.quill.context._
          db2.quill.context.run(
            quote {
              db2.artifacts
            }
          )
        }
        {
          import db1.quill.context._
          db1.quill.context.run(
            quote {
              db1.artifacts.insert(lift(sampleItem))
            }
          )
        }
        {
          import db2.quill.context._
          db2.quill.context.run(
            quote {
              db2.artifacts
            }
          ) shouldBe List(sampleItem)
        }
      }
    }
  }

}
