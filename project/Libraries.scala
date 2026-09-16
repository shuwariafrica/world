import sbt.Keys.version
import sbt.{Compile, Def, IO, *}

object Libraries extends AutoPlugin:
  val scala3 = "3.9.0"
  val munit = "org.scalameta" %% "munit" % "1.3.6"
  val boilerplate = "africa.shuwari" %% "boilerplate" % "0.15.1"
  val `boilerplate-testkit` = "africa.shuwari" %% "boilerplate-testkit" % "0.15.1"
  val `tasty-mima` = "1.4.1"
