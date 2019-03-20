ThisBuild / organization := "ai.mantik"
ThisBuild / version := "0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"

val akkaVersion = "2.5.20"
val akkaHttpVersion = "10.1.7"
val scalaTestVersion = "3.0.5"
val circeVersion = "0.9.3"

import scalariform.formatter.preferences._
val scalariformSettings = {
  scalariformPreferences := scalariformPreferences.value
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DanglingCloseParenthesis, Preserve)
}

val publishSettings = Seq(
  publishTo := {
    val nexus = "https://sonatype.rcxt.de/repository/mantik_maven/"
    if (isSnapshot.value)
      Some("snapshots" at nexus)
    else
      Some("releases"  at nexus)
  },
  publishMavenStyle := true,
  credentials += (sys.env.get("SONATYPE_MANTIK_PASSWORD") match {
    case Some(password) =>
      Credentials("Sonatype Nexus Repository Manager", "sonatype.rcxt.de", "mantik.ci", password)
    case None =>
      Credentials(Path.userHome / ".sbt" / "sonatype.rcxt.de.credentials")
  }),
  test in publish := {},
  test in publishLocal := {}
)

import scala.sys.process._
val gitVersion = "git describe --always".!!.trim
val buildNum = Option(System.getProperty("build.number")).getOrElse("local")

lazy val ds = (project in file("ds"))
  .settings(
    name := "ds",
    libraryDependencies ++= Seq(
      // Circe
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      // Akka
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      // https://mvnrepository.com/artifact/commons-io/commons-io
      "commons-io" % "commons-io" % "2.6",

      // MessagePack
      "org.msgpack" % "msgpack-core" % "0.8.16",

      // SLF4J Api
      "org.slf4j" % "slf4j-api" % "1.7.25",
      // ScalaTest
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
    ),
    scalariformSettings,
    // Disable parallel test execution
    parallelExecution in Test := false,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
  .settings(publishSettings)

lazy val executorApi = (project in file("executor/api"))
  .settings(
    name := "executor-api",
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      // Akka http Circe JSON,
      "de.heikoseeberger" %% "akka-http-circe" % "1.25.2",

      // Circe
      "io.circe" %% "circe-generic" % "0.9.3",
      "io.circe" %% "circe-parser" % "0.9.3",

      // SLF4J Api
      "org.slf4j" % "slf4j-api" % "1.7.25",
      // ScalaTest
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
    ),
    scalariformSettings,
    // Disable parallel test execution
    parallelExecution in Test := false,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
  .settings(publishSettings)

lazy val executorApp = (project in file("executor/app"))
  .dependsOn(executorApi)
  .settings(
    name := "executor-app",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

      // ScalaTest
      "org.scalatest" %% "scalatest" % scalaTestVersion % "test",

      // Kubernetes Client
      "io.skuber" %% "skuber" % "2.1.0",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    ),
    // Disable automatic exection of integration tests
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-l", "ai.mantik.executor.testutils.KubernetesIntegrationTest"),
    // Disable parallel test execution
    parallelExecution in Test := false,
    scalariformSettings
  )
  .settings(publishSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      BuildInfoKey("gitVersion", gitVersion),
      BuildInfoKey("buildNum", buildNum))
    ,
    buildInfoPackage := "ai.mantik.executor.buildinfo"
  )
  .enablePlugins(DockerPlugin, AshScriptPlugin)
  .settings(
    mainClass in Compile := Some("ai.mantik.executor.Main"),
    packageName := "executor",
    dockerExposedPorts := Seq(8080),
    dockerBaseImage := "openjdk:8u191-jre-alpine3.9",
    daemonUserUid in Docker := None,
    daemonUser in Docker := "daemon",
    version in Docker := "latest"
  )


lazy val root = (project in file("."))
  .aggregate(ds, executorApi, executorApp)
  .settings(
    publish := {},
    publishLocal := {},
    test := {}
  )
