package ai.mantik.repository

import ai.mantik.ds.DataType
import ai.mantik.ds.funcational.FunctionType
import ai.mantik.ds.helper.circe.DiscriminatorDependentCodec

import scala.util.matching.Regex

/** A Basic Mantik Definition (algorithms, datasets, etc...) */
sealed trait MantikDefinition {
  def author: Option[String]
  def authorEmail: Option[String]
  def name: Option[String]
  def version: Option[String]
  def directory: Option[String]

  /** Returns violation. */
  def violations: Seq[String] = {
    name.map(MantikDefinition.nameViolations).getOrElse(Nil) ++
      version.map(MantikDefinition.versionViolations).getOrElse(Nil)
  }

  /** Check for no violations or throw an [[InvalidMantikDefinitionException]]. */
  @throws[InvalidMantikDefinitionException]
  def validateOrThrow(): Unit = {
    val v = violations
    if (v.nonEmpty) {
      throw new InvalidMantikDefinitionException(v.mkString(","))
    }
  }

  def kind: String

  def stack: String
}

object MantikDefinition extends DiscriminatorDependentCodec[MantikDefinition] {
  override val subTypes = Seq(
    // Not using constants, they are not yet initialized.
    makeSubType[AlgorithmDefinition]("algorithm", isDefault = true),
    makeSubType[DataSetDefinition]("dataset"),
    makeSubType[TrainableAlgorithmDefinition]("trainable")
  )

  val AlgorithmKind = "algorithm"
  val DataSetKind = "dataset"
  val TrainableAlgorithmKind = "trainable"

  /** Validates the name, returns violations. */
  def nameViolations(name: String): Seq[String] = {
    val violations = Seq.newBuilder[String]
    if (!NameRegex.pattern.matcher(name).matches()) {
      violations += "Invalid Name"
    }
    violations.result()
  }

  /** Validates the version, returns violations. */
  def versionViolations(version: String): Seq[String] = {
    val violations = Seq.newBuilder[String]
    if (!VersionRegex.pattern.matcher(version).matches()) {
      violations += "Invalid Version"
    }
    violations.result()
  }

  /**
   * Regex for a Name.
   * Note: in contrast to account names, also "_" and "." in the middle is allowed
   */
  val NameRegex: Regex = "^[a-z\\d](?:[a-z\\d_\\.]|-(?=[a-z\\d])){0,38}$".r
  /**
   * Regex for a Version.
   * Note: in contrast to account names, also "_" and "." in the middle is allowed
   */
  val VersionRegex: Regex = "^[a-z\\d]([a-z\\d_\\.\\-]*[a-z\\d])?$".r
}

/** An Algorithm Definition inside a Mantikfile. */
case class AlgorithmDefinition(
    // common
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    directory: Option[String] = None,
    // specific
    stack: String,
    `type`: FunctionType
) extends MantikDefinition {
  def kind = MantikDefinition.AlgorithmKind
}

/** A DataSet definition inside a Mantikfile */
case class DataSetDefinition(
    // common
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    directory: Option[String] = None,
    // specific
    format: String,
    `type`: DataType
) extends MantikDefinition {
  def kind = MantikDefinition.DataSetKind

  override def stack: String = format
}

case class TrainableAlgorithmDefinition(
    // common
    author: Option[String] = None,
    authorEmail: Option[String] = None,
    name: Option[String] = None,
    version: Option[String] = None,
    directory: Option[String] = None,
    // specific
    stack: String,
    trainedStack: Option[String] = None, // if not give, stack will be used
    `type`: FunctionType,
    trainingType: DataType,
    statType: DataType
) extends MantikDefinition {
  def kind = MantikDefinition.TrainableAlgorithmKind
}

/** Exception which can be thrown upon illegal Mantik Definitions. */
class InvalidMantikDefinitionException(msg: String) extends RuntimeException(msg)