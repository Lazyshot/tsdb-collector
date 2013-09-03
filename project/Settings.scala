import sbt._
import sbt.Keys._
import com.twitter.sbt._

object Settings {
  lazy val default = Project.defaultSettings ++ StandardProject.newSettings ++ Seq(
    organization := "com.louddoor",
    version := "0.1.0",
    scalaVersion := "2.10.2",

    mainClass in (Compile, run) := Some("com.louddoor.tsdcollector.StatsCollector"),
    mainClass in (Compile, packageBin) := Some("com.louddoor.tsdcollector.StatsCollector")
  )
}