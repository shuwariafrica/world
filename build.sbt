inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.8.3"),
    organization := "africa.shuwari",
    description := "Scala toolkit for representation and manipulation of real-world domain concepts",
    homepage := Some(url("https://github.com/shuwariafrica/world")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    scmInfo := ScmInfo(
      url("https://dev.shuwari.africa/world"),
      "scm:git:https://github.com/shuwariafrica/world.git",
      Some("scm:git:git@github.com:shuwariafrica/world.git")
    ).some
  ) ++ formattingSettings
)

val libraries = new {
  val boilerplate = Def.setting("io.github.arashi01" %%% "boilerplate" % "0.7.0")
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.4")
  val `munit-scalacheck` = Def.setting("org.scalameta" %%% "munit-scalacheck" % "1.2.0")
  val `scala-java-time` = Def.setting("io.github.cquiroz" %%% "scala-java-time" % "2.6.0")
  val `scala-java-time-tzdb` = `scala-java-time`.apply(_.withName("scala-java-time-tzdb"))
}

def compilerSettingsModifier = {
  val options = List[Setting[?]](
    compile / scalacOptions ++= Seq("-Wsafe-init", "-Werror"),
    compile / scalacOptions --= Seq("-Xfatal-warnings")
  )
  inConfig(Compile)(options) ++ inConfig(Test)(options)
}

val `world-common` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/common"))
    .settings(compilerSettingsModifier)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependency(libraries.boilerplate))
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))

val `world-locale` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/locale"))
    .dependsOn(`world-common`)
    .settings(compilerSettingsModifier)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += SourceGenerators.countriesGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.languagesGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.scriptsGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.likelySubtagsGeneratorTask)

val `world-money` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/money"))
    .dependsOn(`world-common`)
    .settings(compilerSettingsModifier)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += SourceGenerators.currenciesGeneratorTask)
    .jsSettings(libraryDependency(libraries.`scala-java-time`(_ % Provided)))
    .jsSettings(libraryDependency(libraries.`scala-java-time-tzdb`(_ % Provided)))
    .nativeSettings(libraryDependency(libraries.`scala-java-time`(_ % Provided)))
    .nativeSettings(libraryDependency(libraries.`scala-java-time-tzdb`(_ % Provided)))

val `world-money-usage` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/money-usage"))
    .dependsOn(`world-locale`, `world-money`)
    .settings(compilerSettingsModifier)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += SourceGenerators.currencyUsageGeneratorTask)

val `world-jvm` =
  project
    .in(file(".jvm"))
    .notPublished
    .aggregate(
      `world-common`.jvm,
      `world-locale`.jvm,
      `world-money`.jvm,
      `world-money-usage`.jvm
    )

val `world-native` =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(
      `world-common`.native,
      `world-locale`.native,
      `world-money`.native,
      `world-money-usage`.native
    )

val `world-js` =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(
      `world-common`.js,
      `world-locale`.js,
      `world-money`.js,
      `world-money-usage`.js
    )

val `world-root` =
  project
    .in(file("."))
    .shuwariProject
    .notPublished
    .apacheLicensed
    .enablePlugins(ScalaUnidocPlugin, WorldUnidocPlugin)
    .aggregate(`world-jvm`, `world-js`, `world-native`)
    .settings(
      // Aggregate ScalaUnidoc from JVM projects only
      ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(`world-js`, `world-native`)
    )

<<<<<<< HEAD
||||||| 568ce7f
inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.7.3"),
    organization := "africa.shuwari",
    description := "Scala toolkit for representation and manipulation of real-world domain concepts",
    homepage := Some(url("https://github.com/shuwarifrica/world")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    sonatypeCredentialHost := Sonatype.sonatypeCentralHost,
    publishCredentials,
    scmInfo := ScmInfo(
      url("https://dev.shuwari.africa/world"),
      "scm:git:https://github.com/shuwariafrica/world.git",
      Some("scm:git:git@github.com:shuwariafrica/world.git")
    ).some
  ) ++ formattingSettings
)

=======
inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.7.4"),
    organization := "africa.shuwari",
    description := "Scala toolkit for representation and manipulation of real-world domain concepts",
    homepage := Some(url("https://github.com/shuwarifrica/world")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    sonatypeCredentialHost := Sonatype.sonatypeCentralHost,
    publishCredentials,
    scmInfo := ScmInfo(
      url("https://dev.shuwari.africa/world"),
      "scm:git:https://github.com/shuwariafrica/world.git",
      Some("scm:git:git@github.com:shuwariafrica/world.git")
    ).some
  ) ++ formattingSettings
)

>>>>>>> upstream/main
def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += libraries.munit.value % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

def formattingSettings =
  List(
    scalafmtDetailedError := true,
    scalafmtPrintDiff := true
  )

def libraryDependency(library: Def.Initialize[ModuleID]) = libraryDependencies += library.value

def publishSettings = pgpSettings ++: List(
  packageOptions += Package.ManifestAttributes(
    "Created-By" -> "Simple Build Tool",
    "Built-By" -> System.getProperty("user.name"),
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> Keys.version.value,
    "Specification-Vendor" -> organizationName.value,
    "Implementation-Title" -> name.value,
    "Implementation-Version" -> fullVersion.value,
    "Implementation-Vendor-Id" -> organization.value,
    "Implementation-Vendor" -> organizationName.value
  ),
  publishTo := {
    if (isSnapshot.value)
      Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
    else localStaging.value
  },
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true
)

def pgpSettings = List(
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

addCommandAlias("format", "project world-jvm; scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "project world-jvm; scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
