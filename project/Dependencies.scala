import sbt._
import sbt.Keys._

object Dependencies {
  private lazy val finagleVer = "6.5.0"

  lazy val twitterServer =  "com.twitter"      %% "twitter-server"        % "1.0.3"
  lazy val finagleCore =    "com.twitter"      %% "finagle-core"          % finagleVer

  lazy val finagleStack = Seq(
    twitterServer,
    finagleCore
  )
}