addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.8.0")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.6")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("africa.shuwari.sbt" % "sbt-shuwari" % "0.14.2")
addSbtPlugin("africa.shuwari.sbt" % "sbt-shuwari-js" % "0.14.2")
addSbtPlugin("africa.shuwari.sbt" % "sbt-shuwari-cross" % "0.14.2")
addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "3.12.2")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.1")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")

libraryDependencies += "com.github.tototoshi" %% "scala-csv" % "2.0.0"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-generic" % "0.14.15",
  "io.circe" %% "circe-yaml" % "1.15.0"
)
