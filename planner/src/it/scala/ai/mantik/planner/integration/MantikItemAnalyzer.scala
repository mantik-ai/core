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
package ai.mantik.planner.integration

import ai.mantik.planner._

/** Helper for analyzing MantikItems
  * TODO: Perhaps this could go into the MantikItem's API
  * but right now we only need this for testing.
  */
@deprecated("mnp", "Can be solved via MantikState")
case class MantikItemAnalyzer(
    item: MantikItem
)(implicit context: PlanningContext) {

  /** Return true if the item is requested for caching
    * (This doesn't have to mean that the cache is evaluated)
    */
  def isCached: Boolean = {
    def check(payloadSource: PayloadSource): Boolean = {
      payloadSource match {
        case _: PayloadSource.Cached     => true
        case p: PayloadSource.Projection => check(p.source)
        case _                           => false
      }
    }
    check(item.core.source.payload)
  }

  /** Return true if the item's payload is inside the cache. */
  def isCacheEvaluated: Boolean = {
    cacheFile.isDefined
  }

  def cacheFile: Option[FileId] = {
    context.state(item).cacheFile
  }
}
