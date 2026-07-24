addSbtPlugin("africa.shuwari" % "sbt-version" % "0.9.0")
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.9.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")
addSbtPlugin("africa.shuwari.sbt" % "sbt-shuwari" % "0.15.4")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.7")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.22.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.12")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.6")

// TASTy-MiMa publishes no sbt 2.x plugin. The sbt 2.x build definition is itself Scala 3,
// so the engine links directly onto the build classpath (see Compat.scala).
libraryDependencies += "ch.epfl.scala" %% "tasty-mima" % "1.4.1"
