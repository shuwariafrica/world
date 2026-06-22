import sbt.*

object Dependencies:
  val scalaVersion = "3.8.4"
  val boilerplate = "io.github.arashi01" %% "boilerplate" % "0.7.0"
  val munit = "org.scalameta" %% "munit" % "1.3.3"
  val `munit-scalacheck` = "org.scalameta" %% "munit-scalacheck" % "1.2.0"
  val `scala-java-time` = "io.github.cquiroz" %% "scala-java-time" % "2.7.0"
  val `scala-java-time-tzdb` = `scala-java-time`.withName("scala-java-time-tzdb")
