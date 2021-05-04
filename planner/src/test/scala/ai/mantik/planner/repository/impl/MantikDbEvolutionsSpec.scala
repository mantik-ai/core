/*
 * This file is part of the Mantik Project.
 * Copyright (c) 2020-2021 Mantik UG (Haftungsbeschränkt)
 * Authors: See AUTHORS file
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.
 *
 * Additionally, the following linking exception is granted:
 *
 * If you modify this Program, or any covered work, by linking or
 * combining it with other code, such other code is not for that reason
 * alone subject to any of the requirements of the GNU Affero GPL
 * version 3.
 *
 * You can be released from the requirements of the license by purchasing
 * a commercial license.
 */
package ai.mantik.planner.repository.impl

import java.nio.charset.StandardCharsets

import ai.mantik.planner.utils.sqlite.{QuillSqlite, SqliteEvolutions, SqliteEvolutionsSpecBase}
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
