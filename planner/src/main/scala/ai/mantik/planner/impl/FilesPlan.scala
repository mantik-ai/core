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
package ai.mantik.planner.impl

import ai.mantik.planner.{PlanFileReference, PlanOp}

/** References a file with it's associated content type. */
private[impl] case class PlanFileWithContentType(
    ref: PlanFileReference,
    contentType: String
)

/**
  * References multiple data streams available as files.
  * @param preOp the plan necessary to make it available.
  * @param files the files which make it available.
  */
private[impl] case class FilesPlan(
    preOp: PlanOp[Unit] = PlanOp.Empty,
    files: IndexedSeq[PlanFileWithContentType] = IndexedSeq.empty
) {

  /** Return the file references. */
  def fileRefs: IndexedSeq[PlanFileReference] = files.map(_.ref)
}
