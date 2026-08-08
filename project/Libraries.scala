import sbt.*

object Libraries:
  val scala3 = "3.9.0-RC1"

  val munit = "org.scalameta" %% "munit" % "1.3.4"

  // The ecosystem substrate world sits above: the typed-error base, the scalar wire-text contract,
  // the locale-free codec vocabulary, and the null-elimination utilities.
  val boilerplate = "africa.shuwari" %% "boilerplate" % "0.13.0"

  val `boilerplate-testkit` = "africa.shuwari" %% "boilerplate-testkit" % "0.13.0"

  val `tasty-mima` = "1.4.1"
end Libraries
