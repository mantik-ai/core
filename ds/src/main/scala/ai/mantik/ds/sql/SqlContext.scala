package ai.mantik.ds.sql

import ai.mantik.ds.TabularData

/**
  * Context for translating SQL Expressions
  *
  * @param anonymous anonymous sources
  */
case class SqlContext(
    anonymous: Vector[TabularData] = Vector.empty
)
