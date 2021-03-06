addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.2")
addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("com.github.sbt" % "sbt-native-packager" % "1.9.4")

evictionErrorLevel := Level.Warn

// https://scalapb.github.io/sbt-settings.html
addSbtPlugin("com.thesamet" % "sbt-protoc" % "1.0.5")
libraryDependencies ++= Seq(
  "com.thesamet.scalapb" %% "compilerplugin" % "0.11.8"
)

addSbtPlugin("au.com.onegeek" %% "sbt-dotenv" % "2.0.117")
addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.5.3")

addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.3")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.0")

// Publishing to Sonatype
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.1.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.9.9")

// Dependency Graph, partly broken with SBT 1.3.x, remove with update of SBT
addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.10.0-RC1")
