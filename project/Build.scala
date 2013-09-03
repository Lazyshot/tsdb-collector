import sbt._
import sbt.Keys._
import com.twitter.sbt._

object ProjectBuild extends Build {
  import Dependencies._

  lazy val server = Project(
    id = "server",
    base = file("."),
    settings = Settings.default ++ Seq(
      name := "tsdcollector",
      libraryDependencies ++= finagleStack,
      resolvers += "twitter" at "http://maven.twttr.com/"
    )
  )
}