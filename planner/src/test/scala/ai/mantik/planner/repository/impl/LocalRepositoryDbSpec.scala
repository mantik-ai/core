package ai.mantik.planner.repository.impl

import ai.mantik.elements.ItemId
import ai.mantik.planner.repository.impl.LocalRepositoryDb.DbMantikItem
import ai.mantik.testutils.{TempDirSupport, TestBase}

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

  val sampleItem = DbMantikItem(
    itemId = ItemId.generate().toString,
    fileId = Some("1"),
    mantikheader = "blabla",
    kind = "kind",
    executorStorageId = Some("123")
  )

  it should "store elements" in {
    withDb { db =>
      import db.quill.context._
      val items = db.quill.context.run(
        quote {
          db.items
        }
      )
      items shouldBe empty

      db.quill.context.run {
        quote {
          db.items.insert(lift(sampleItem))
        }
      }

      val back = db.quill.context.run(
        quote {
          db.items
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
              db2.items
            }
          )
        }
        {
          import db1.quill.context._
          db1.quill.context.run(
            quote {
              db1.items.insert(lift(sampleItem))
            }
          )
        }
        {
          import db2.quill.context._
          db2.quill.context.run(
            quote {
              db2.items
            }
          ) shouldBe List(sampleItem)
        }
      }
    }
  }

}
