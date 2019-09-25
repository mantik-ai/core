package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets

import ai.mantik.planner.utils.sqlite.{ QuillSqlite, SqliteEvolutions, SqliteEvolutionsSpecBase }
import org.apache.commons.io.IOUtils

class MantikDbEvolutionsSpec extends SqliteEvolutionsSpecBase {

  override protected def generateEvolutions(quill: QuillSqlite): SqliteEvolutions = new MantikDbEvolutions(quill)

  it should "detect number one as fresh" in new Env {
    dbEvolutions.dbVersion() shouldBe 0

    db.runSqlInTransaction(
      IOUtils.resourceToString("/ai.mantik.planner.repository/evolution/01.sql", StandardCharsets.UTF_8)
    )
    dbEvolutions.dbVersion() shouldBe 1
  }
}
