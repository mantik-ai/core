ThisBuild / organization := "ai.mantik"
ThisBuild / version := "0.2-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.8"
// ThisBuild / scalacOptions += "-Xfatal-warnings" // this breaks the doc target due https://github.com/scala/bug/issues/10134
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions += "-deprecation"
ThisBuild / scalacOptions += "-Ypartial-unification" // Needed for Cats
// ThisBuild / updateOptions := updateOptions.value.withGigahorse(false) // See https://github.com/sbt/sbt/issues/3570

Test / parallelExecution := true
Test / fork := false
IntegrationTest / parallelExecution := false
IntegrationTest / fork := true

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

val akkaVersion = "2.5.20"
val akkaHttpVersion = "10.1.7"
val scalaTestVersion = "3.0.5"
val circeVersion = "0.11.1"
val slf4jVersion = "1.7.25"
val fhttpVersion = "0.2.2"

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
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
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

def configureBuildInfo(packageName: String): Seq[Def.Setting[_]] =
  Seq(
    buildInfoKeys := Seq[BuildInfoKey](
    name,
    version,
    scalaVersion,
    BuildInfoKey("gitVersion", gitVersion),
    BuildInfoKey("buildNum", buildNum)
    ),
    buildInfoPackage := packageName
  )

// Initializes a sub project with common settings
def makeProject(directory: String, id: String = "") = {
  val idToUse = if (id.isEmpty){
    directory.split("/").last
  } else {
    id
  }
  sbt.Project(idToUse, file(directory))
    .dependsOn(testutils % "it,test")
    .configs(IntegrationTest)
    .settings(
      scalariformSettings,
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
      publishSettings,
      Defaults.itSettings,
      IntegrationTest / testOptions += Tests.Argument("-oDF")
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

      // Cats
      "org.typelevel" %% "cats-core" % "1.6.0",

      // Parboiled (Parsers)
      "org.parboiled" %% "parboiled" % "2.1.5",

      // https://mvnrepository.com/artifact/commons-io/commons-io
      "commons-io" % "commons-io" % "2.6",

      // MessagePack
      "org.msgpack" % "msgpack-core" % "0.8.16",

      // SLF4J Api
      "org.slf4j" % "slf4j-api" % slf4jVersion
    )
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
      "io.circe" %% "circe-generic-extras" % circeVersion,

      "de.heikoseeberger" %% "akka-http-circe" % "1.25.2"
    )
  )

lazy val mnpScala = makeProject("mnp/mnpscala")
  .dependsOn(componently)
  .settings(
    name := "mnpscala",
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-api" % scalapb.compiler.Version.grpcJavaVersion
    ),
    publishSettings,
    enableProtocolBuffer,
    PB.protoSources in Compile := Seq(baseDirectory.value / "../protocol/protobuf")
)

lazy val elements = makeProject("elements")
  .dependsOn(ds, util)
  .settings(
    name := "elements",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.8.0",
      "net.reactivecore" %% "fhttp-akka" % fhttpVersion,
      "io.grpc" % "grpc-api" % scalapb.compiler.Version.grpcJavaVersion
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
    )
  )


lazy val executorApi = makeProject("executor/api", "executorApi")
  .dependsOn(componently)
  .settings(
    name := "executor-api"
  )

lazy val executorCommon = makeProject("executor/common", "executorCommon")
  .dependsOn(executorApi)
  .settings(
    name := "executor-common"
  )

lazy val executorCommonTest = makeProject("executor/common-test", "executorCommonTest")
  .dependsOn(testutils, executorApi, executorCommon)
  .settings(
    name := "executor-common-test"
  )

lazy val executorDocker = makeProject("executor/docker", "executorDocker")
  .dependsOn(executorApi, executorCommon)
  .dependsOn(executorCommonTest % "it")
  .settings(
    name := "executor-docker",
    libraryDependencies ++= Seq(
      "net.reactivecore" %% "fhttp-akka" % fhttpVersion
    )
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(configureBuildInfo("ai.mantik.executor.docker.buildinfo"))

lazy val executorKubernetes = makeProject("executor/kubernetes", "executorKubernetes")
  .dependsOn(executorApi, executorCommon)
  .dependsOn(executorCommonTest % "it")
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
    )
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(configureBuildInfo("ai.mantik.executor.kubernetes.buildinfo"))

lazy val planner = makeProject("planner")
  .dependsOn(ds, elements, executorApi, componently, mnpScala)
  .dependsOn(executorKubernetes % "it", executorDocker % "it")
  .settings(
    name := "planner",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "org.xerial" % "sqlite-jdbc" % "3.28.0",
      "io.getquill" %% "quill-jdbc" % "3.2.0"
    ),
    enableProtocolBuffer,
    configureBuildInfo("ai.mantik.planner.buildinfo"),
    PB.protoSources in Compile += baseDirectory.value / "../bridge/protocol/protobuf"
  )
  .enablePlugins(BuildInfoPlugin)


lazy val examples = makeProject("examples")
  .dependsOn(engine)
  .settings(
    name := "examples",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    ),
    publish := {},
    publishLocal := {}
  )

/** Engine implementation and API. */
lazy val engine = makeProject("engine")
  .dependsOn(planner, executorKubernetes % "it", executorDocker % "it")
  .settings(
    name := "engine"
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(configureBuildInfo("ai.mantik.engine.buildinfo"))
  .settings(
    enableProtocolBuffer
  )

/** Runable Main Class */
lazy val engineApp = makeProject("engine-app", "engineApp")
  .dependsOn(engine, executorKubernetes, executorDocker)
  .settings(
    name := "engine-app"
  )
  .settings(
    libraryDependencies ++= Seq(
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    )
  )

lazy val root = (project in file("."))
  .aggregate(
    testutils,
    ds,
    mnpScala,
    elements,
    executorApi,
    executorCommon,
    executorKubernetes,
    executorDocker,
    examples,
    planner,
    engine,
    engineApp,
    componently,
    util
  )
  .settings(
    name := "mantik-core",
    publish := {},
    publishLocal := {},
    test := {}
  )
