import scala.sys.process._
val gitVersion = "git describe --always".!!.trim
val buildNum = Option(System.getProperty("build.number")).getOrElse("local")

// If there is a Tag starting with v, e.g. v0.3.0 use it as the build artefact version (e.g. 0.3.0)
val versionTag = sys.env
  .get("CI_COMMIT_TAG")
  .filter(_.startsWith("v"))
  .map(_.stripPrefix("v"))

val snapshotVersion = "0.4-SNAPSHOT"
val artefactVersion = versionTag.getOrElse(snapshotVersion)

ThisBuild / organization := "ai.mantik"
ThisBuild / version := artefactVersion
ThisBuild / scalaVersion := "2.12.13"
// ThisBuild / scalacOptions += "-Xfatal-warnings" // this breaks the doc target due https://github.com/scala/bug/issues/10134
ThisBuild / scalacOptions += "-feature"
ThisBuild / scalacOptions ++= Seq("-unchecked", "-deprecation")
ThisBuild / scalacOptions += "-Ypartial-unification" // Needed for Cats
ThisBuild / autoAPIMappings := true
ThisBuild / publishArtifact in (Compile, packageDoc) := false

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
val shapelessVersion = "2.3.3"
val scalaLoggingVersion = "3.9.3"
val commonsIoVersion = "2.8.0"
val guavaVersion = "30.1.1-jre"
val guiceVersion = "5.0.1"
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
val metricsVersion = "4.2.0"

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
      .gen(
        lenses = false
      ) -> (sourceManaged in Compile).value / "protobuf" // see https://github.com/thesamet/sbt-protoc/issues/6
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
      "com.chuusai" %% "shapeless" % shapelessVersion,
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

// Contains the Mantik Bridge Specific Protobuf Elements (except MNP)
lazy val bridgeProtocol = makeProject("bridge/protocol", "bridgeProtocol")
  .dependsOn(mnpScala)
  .settings(
    name := "bridge-protocol",
    enableProtocolBuffer,
    PB.protoSources in Compile += baseDirectory.value / "protobuf"
  )

lazy val planner = makeProject("planner")
  .dependsOn(ds, elements, executorApi, componently, mnpScala, bridgeProtocol, scalaFnApi, uiApi)
  .dependsOn(executorKubernetes % "it", executorDocker % "it", executorS3Storage % "it")
  .settings(
    name := "planner",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test,
      "org.xerial" % "sqlite-jdbc" % sqliteJdbcVersion,
      "io.getquill" %% "quill-jdbc" % quillVersion,
      "io.dropwizard.metrics" % "metrics-core" % metricsVersion
    ),
    enableProtocolBuffer,
    configureBuildInfo("ai.mantik.planner.buildinfo")
  )
  .enablePlugins(BuildInfoPlugin)

// The Model of the UI
lazy val uiApi = makeProject("ui/api", "uiApi")
  .dependsOn(ds, elements, componently)
  .settings(
    name := "ui-api"
  )

// The Server, including wrapped HTML Code
lazy val uiServer = makeProject("ui/server", "uiServer")
  .dependsOn(uiApi)
  .settings(
    name := "ui-server",
    Compile / unmanagedResourceDirectories += baseDirectory.value / ".." / "client" / "target"
  )

lazy val examples = makeProject("examples/scala")
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
  .dependsOn(engine, executorKubernetes, executorDocker, executorS3Storage, uiServer)
  .settings(
    name := "engine-app"
  )
  .settings(
    libraryDependencies ++= Seq(
      // Logging
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
    ),
    // Do not run within SBT but in a forked VM when starting
    fork := true
  )
  .enablePlugins(JavaAppPackaging)

lazy val scalaFnClosureSerializer = makeProject("bridge/scalafn/closure-serializer", "scalaFnClosureSerializer")
  .settings(
    name := "scala-fn-closure-serializer",
    libraryDependencies ++= Seq(
      "com.twitter" %% "chill" % "0.9.5",
      "org.slf4j" % "slf4j-api" % slf4jVersion
    )
  )

lazy val scalaFnApi = makeProject("bridge/scalafn/api", "scalaFnApi")
  .dependsOn(ds, elements, scalaFnClosureSerializer)
  .settings(
    name := "scala-fn-api"
  )

lazy val scalaFnBridge = makeProject("bridge/scalafn/bridge", "scalaFnBridge")
  .dependsOn(scalaFnApi, bridgeProtocol)
  .settings(
    name := "scala-fn-bridge",
    enableProtocolBuffer,
    configureBuildInfo("ai.mantik.bridge.scalafn.buildinfo"),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % logbackVersion
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
    executorCommonTest,
    executorKubernetes,
    executorDocker,
    executorS3Storage,
    examples,
    bridgeProtocol,
    planner,
    uiApi,
    uiServer,
    engine,
    engineApp,
    componently,
    util,
    scalaFnClosureSerializer,
    scalaFnApi,
    scalaFnBridge
  )
  .settings(
    name := "mantik-core",
    publish := {},
    publishLocal := {},
    test := {},
    addCompilerPlugin("org.scalamacros" % "paradise" % macroParadiseVersion cross CrossVersion.full)
  )
  .enablePlugins(ScalaUnidocPlugin)
  .settings(
    // Exclude examples from Documentation
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(examples, testutils)
  )
