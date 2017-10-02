import sbt.Keys._

val akkaV = "2.5.1"
lazy val root = (project in file("."))
  .settings(
    organization := "com.example",
    version := "1.0.0",
    scalaVersion := "2.12.2",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaV,
      "com.typesafe.akka" %% "akka-stream" % akkaV
    )
  )
