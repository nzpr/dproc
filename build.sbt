ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.10"

lazy val root = (project in file(".")).settings(name := "lazo")

libraryDependencies += "co.fs2"        %% "fs2-core"                      % "3.6.1"
libraryDependencies += "org.typelevel" %% "cats-core"                     % "2.9.0"
libraryDependencies += "org.typelevel" %% "cats-effect"                   % "3.4.7"
libraryDependencies += "org.typelevel" %% "mouse"                         % "1.2.1"
libraryDependencies += "org.scalatest" %% "scalatest"                     % "3.2.15" % "test"
libraryDependencies += "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0" % Test

scalacOptions ++= Seq("-Xmigration", "-Ymacro-annotations")

enablePlugins(JmhPlugin)
