package ai.mantik.planner.impl

import ai.mantik.planner.PlanOp

trait PlanTestUtils {

  def splitOps(op: PlanOp[_]): Seq[PlanOp[_]] = {
    PlanOp.compress(op) match {
      case PlanOp.Sequential(parts, last) => parts :+ last
      case PlanOp.Empty                   => Nil
      case other                          => Seq(other)
    }
  }
}
