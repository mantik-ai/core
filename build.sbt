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
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "commons-io" % "commons-io" % "2.6",
    ),
    publish := {},
    publishLocal := {}
  )

lazy val IntegrationTest = config("it") extend(Test)

val testSettings = Seq (
  testOptions in Test := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-l", "ai.mantik.testutils.tags.IntegrationTest")),
  testOptions in IntegrationTest := Seq(Tests.Argument(TestFrameworks.ScalaTest, "-n", "ai.mantik.testutils.tags.IntegrationTest")),
  parallelExecution in Test := false,
  parallelExecution in IntegrationTest := false
) ++ inConfig(IntegrationTest)(Defaults.testTasks)

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
      "org.slf4j" % "slf4j-api" % "1.7.25"
    ),
    publishSettings
  )

lazy val elements = makeProject("elements")
  .dependsOn(ds)
  .settings(
    name := "elements",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-yaml" % "0.8.0"
    ),
    publishSettings
  )

lazy val executorApi = makeProject("executor/api", "executorApi")
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

      // SLF4J Api
      "org.slf4j" % "slf4j-api" % "1.7.25",
    ),
    publishSettings
  )


lazy val executorApp = makeProject("executor/app", "executorApp")
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

      // Kubernetes Client
      "io.skuber" %% "skuber" % "2.1.0",

      // Logging
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
      "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
      
      // Guava
      "com.google.guava" % "guava" % "27.1-jre",
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
  .dependsOn(ds, elements, executorApi)
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
    publishSettings
  )


lazy val examples = makeProject("examples")
  .dependsOn(planner)
  .settings(
    name := "examples",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    publish := {},
    publishLocal := {}
  )

lazy val engine = makeProject("engine")
  .dependsOn(planner, executorApi)
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
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    )
  )  
  .settings(
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
    ),
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value / "protobuf" // see https://github.com/thesamet/sbt-protoc/issues/6
    )
  )

lazy val root = (project in file("."))
  .aggregate(testutils, ds, elements, executorApi, executorApp, examples, planner, engine)
  .settings(
    name := "mantik-core",
    publish := {},
    publishLocal := {},
    test := {}
  )
