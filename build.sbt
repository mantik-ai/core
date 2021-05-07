ThisBuild / organization := "ai.mantik"
ThisBuild / version := "0.3-SNAPSHOT"
ThisBuild / scalaVersion := "2.12.13"
// ThisBuild / scalacOptions += "-Xfatal-warnings" // this breaks the doc target due https://github.com/scala/bug/issues/10134
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")
ThisBuild / scalacOptions += "-Ypartial-unification" // Needed for Cats

Test / parallelExecution := true
Test / fork := false
IntegrationTest / parallelExecution := false
IntegrationTest / fork := true

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

val akkaVersion = "2.5.32"
val akkaHttpVersion = "10.1.14"
val scalaTestVersion = "3.0.9"
val circeVersion = "0.11.2"
val slf4jVersion = "1.7.30"
val fhttpVersion = "0.2.2"
val scalaLoggingVersion = "3.9.3"
val commonsIoVersion = "2.8.0"
val guavaVersion = "30.1.1-jre"
val guiceVersion = "4.2.3"
val circeYamlVersion = "0.8.0"
val logbackVersion = "1.2.3"
val macroParadiseVersion = "2.1.1"
val akkaHttpCirceVerson = "1.25.2"
val awsSdkVersion = "2.16.45"
val skuberVersion = "2.3.0"
val sqliteJdbcVersion = "3.28.0"
val quillVersion = "3.2.2"
val catsVersion = "1.6.1"
val parboiledVersion = "2.1.8"
val msgpackVersion = "0.8.22"

val publishSettings = Seq(
  publishTo := {
    val nexus = "https://sonatype.rcxt.de/repository/mantik_maven/"
    if (isSnapshot.value)
      Some("snapshots" at nexus)
    else
      Some("releases" at nexus)
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
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "commons-io" % "commons-io" % commonsIoVersion,
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "io.circe" %% "circe-yaml" % circeYamlVersion
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
    scalapb
      .gen() -> (sourceManaged in Compile).value / "protobuf" // see https://github.com/thesamet/sbt-protoc/issues/6
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
  import org.scalafmt.sbt.ScalafmtPlugin._

  val idToUse = if (id.isEmpty) {
    directory.split("/").last
  } else {
    id
  }
  sbt
    .Project(idToUse, file(directory))
    .dependsOn(testutils % "it,test")
    .configs(IntegrationTest)
    .settings(
      addCompilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full),
      publishSettings,
      Defaults.itSettings,
      IntegrationTest / testOptions += Tests.Argument("-oDF"),
      inConfig(IntegrationTest)(scalafmtConfigSettings)
    )
}

lazy val ds = makeProject("ds")
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
      // Cats
      "org.typelevel" %% "cats-core" % catsVersion,
      // Parboiled (Parsers)
      "org.parboiled" %% "parboiled" % parboiledVersion,
      // https://mvnrepository.com/artifact/commons-io/commons-io
      "commons-io" % "commons-io" % commonsIoVersion,
      // MessagePack
      "org.msgpack" % "msgpack-core" % msgpackVersion,
      // SLF4J Api
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      // Guava
      "com.google.guava" % "guava" % guavaVersion
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
      "io.circe" %% "circe-generic-extras" % circeVersion
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
      "io.circe" %% "circe-yaml" % circeYamlVersion,
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
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
      // gRPC
      "io.grpc" % "grpc-stub" % scalapb.compiler.Version.grpcJavaVersion,
      "com.google.protobuf" % "protobuf-java" % scalapb.compiler.Version.protobufVersion,
      // Guice
      "com.google.inject" % "guice" % guiceVersion
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

lazy val executorS3Storage = makeProject("executor/s3-storage", "executorS3Storage")
  .dependsOn(testutils, executorApi, executorCommon)
  .settings(
    name := "executor-s3-storage",
    libraryDependencies ++= Seq(
      // Amazon S3
      "software.amazon.awssdk" % "s3" % awsSdkVersion
    )
  )

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
      "io.skuber" %% "skuber" % skuberVersion,
      // Guava
      "com.google.guava" % "guava" % guavaVersion,
      "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion
    )
  )
  .enablePlugins(BuildInfoPlugin)
  .settings(configureBuildInfo("ai.mantik.executor.kubernetes.buildinfo"))

lazy val planner = makeProject("planner")
  .dependsOn(ds, elements, executorApi, componently, mnpScala)
  .dependsOn(executorKubernetes % "it", executorDocker % "it", executorS3Storage % "it")
  .settings(
    name := "planner",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "org.xerial" % "sqlite-jdbc" % sqliteJdbcVersion,
      "io.getquill" %% "quill-jdbc" % quillVersion
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
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    ),
    publish := {},
    publishLocal := {}
  )

/** Engine implementation and API. */
lazy val engine = makeProject("engine")
  .dependsOn(planner, executorKubernetes % "it", executorDocker % "it", executorS3Storage % "it")
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
  .dependsOn(engine, executorKubernetes, executorDocker, executorS3Storage)
  .settings(
    name := "engine-app"
  )
  .settings(
    libraryDependencies ++= Seq(
      // Logging
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    )
  )
  .enablePlugins(JavaAppPackaging)

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
    executorS3Storage,
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
