ThisBuild / organization := "ai.mantik"
ThisBuild / version := "0.1-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"
// ThisBuild / scalacOptions += "-Xfatal-warnings" // this breaks the doc target due https://github.com/scala/bug/issues/10134
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions += "-deprecation"
ThisBuild / scalacOptions += "-Ypartial-unification" // Needed for Cats
ThisBuild / updateOptions := updateOptions.value.withGigahorse(false) // See https://github.com/sbt/sbt/issues/3570

val akkaVersion = "2.5.20"
val akkaHttpVersion = "10.1.7"
val scalaTestVersion = "3.0.5"
val circeVersion = "0.11.1"
val slf4jVersion = "1.7.25"

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

// Shared test code
lazy val testutils = (project in file("testutils"))
  .settings(
    name := "testutils",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "commons-io" % "commons-io" % "2.6",
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "io.circe" %% "circe-yaml" % "0.8.0"
    ),
    publishSettings
  )

lazy val IntegrationTest = config("it") extend(Test)

val testSettings = Seq (
  testOptions in Test := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-l", "ai.mantik.testutils.tags.IntegrationTest")),
  testOptions in IntegrationTest := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-n", "ai.mantik.testutils.tags.IntegrationTest")),
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false
) ++ inConfig(IntegrationTest)(Defaults.testTasks)

def enableProtocolBuffer = Seq(
  libraryDependencies ++= Seq(
    "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  ),
  PB.targets in Compile := Seq(
    scalapb.gen() -> (sourceManaged in Compile).value / "protobuf" // see https://github.com/thesamet/sbt-protoc/issues/6
  )
)

// Initializes a sub project with common settings
def makeProject(directory: String, id: String = "") = {
  val idToUse = if (id.isEmpty){
    directory.split("/").last
  } else {
    id
  }
  sbt.Project(idToUse, file(directory))
    .dependsOn(testutils % "test")
    .configs(IntegrationTest)
    .settings(
      scalariformSettings,
      testSettings,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    )
}

lazy val ds = makeProject("ds")
  .settings(
    name := "ds",
    libraryDependencies ++= Seq(
      // Circe
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,

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
      "org.slf4j" % "slf4j-api" % slf4jVersion
    ),
    publishSettings
  )

// Utility stuff, may only depend on Akka, Http and JSON.
lazy val util = makeProject("util")
  .settings(
    name := "util",
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      // Akka
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      // Circe
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-java8" % circeVersion,

      "de.heikoseeberger" %% "akka-http-circe" % "1.25.2"
    ),
    publishSettings
  )

lazy val elements = makeProject("elements")
  .dependsOn(ds, util)
  .settings(
    name := "elements",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.8.0"
    ),
    publishSettings
  )

// Helper library for component building based upon gRpc and Guice
lazy val componently = makeProject("componently")
  .dependsOn(util)
  .settings(
    name := "componently",
    libraryDependencies ++= Seq(
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",

      // gRPC
      "io.grpc" % "grpc-stub" % scalapb.compiler.Version.grpcJavaVersion,
      "com.google.protobuf" % "protobuf-java" % scalapb.compiler.Version.protobufVersion,

      // Guice
      "com.google.inject" % "guice" % "4.2.2"
    ),
    publishSettings
  )


lazy val executorApi = makeProject("executor/api", "executorApi")
  .dependsOn(componently)
  .settings(
    name := "executor-api",
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      // Akka http Circe JSON,
      "de.heikoseeberger" %% "akka-http-circe" % "1.25.2",

      // Circe
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,

      // Logging
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    ),
    publishSettings
  )

lazy val executorKubernetes = makeProject("executor/kubernetes", "executorKubernetes")
  .dependsOn(executorApi)
  .settings(
    name := "executor-kubernetes",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,

      "com.typesafe.akka" %% "akka-stream" % akkaVersion,
      "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % Test,

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,

      // Kubernetes Client
      "io.skuber" %% "skuber" % "2.2.0",

      // Guava
      "com.google.guava" % "guava" % "27.1-jre",

      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
    ),
    publishSettings
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      BuildInfoKey("gitVersion", gitVersion),
      BuildInfoKey("buildNum", buildNum))
    ,
    buildInfoPackage := "ai.mantik.executor.kubernetes.buildinfo"
  )

lazy val executorApp = makeProject("executor/app", "executorApp")
  .dependsOn(executorKubernetes)
  .settings(
    name := "executor-app",
    publishSettings
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    libraryDependencies ++= Seq(
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    )
  )
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
    dockerExposedPorts := Seq(8085),
    dockerBaseImage := "openjdk:8u191-jre-alpine3.9",
    daemonUserUid in Docker := None,
    daemonUser in Docker := "daemon",
    version in Docker := "latest"
  )

lazy val planner = makeProject("planner")
  .dependsOn(ds, elements, executorApi, componently)
  .dependsOn(executorApp % "test")
  .settings(
    name := "planner",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "1.6.0",

      // Parboiled (Parsers)
      "org.parboiled" %% "parboiled" % "2.1.5",

      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "org.xerial" % "sqlite-jdbc" % "3.18.0",
      "io.getquill" %% "quill-jdbc" % "3.2.0"
    ),
    publishSettings,
    enableProtocolBuffer
  )


lazy val examples = makeProject("examples")
  .dependsOn(engine)
  .settings(
    name := "examples",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    publish := {},
    publishLocal := {}
  )

lazy val engine = makeProject("engine")
  .dependsOn(planner, executorKubernetes)
  .dependsOn(executorApp % "test")
  .settings(
    name := "engine",
    publishSettings
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      version,
      scalaVersion,
      BuildInfoKey("gitVersion", gitVersion),
      BuildInfoKey("buildNum", buildNum))
    ,
    buildInfoPackage := "ai.mantik.engine.buildinfo"
  )
  .settings(
    enableProtocolBuffer
  )
  .settings(
    libraryDependencies ++= Seq(
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    )
  )

lazy val root = (project in file("."))
  .aggregate(testutils, ds, elements, executorApi, executorKubernetes, executorApp, examples, planner, engine, componently, util)
  .settings(
    name := "mantik-core",
    publish := {},
    publishLocal := {},
    test := {}
  )
