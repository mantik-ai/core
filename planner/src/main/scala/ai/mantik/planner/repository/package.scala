package ai.mantik.planner

package object repository {
  /** A Mantik Artifact together with all of it's dependencies */
  type MantikArtifactWithHull = (MantikArtifact, Seq[MantikArtifact])
}
