organization := "africa.shuwari"
description := "Scala toolkit for representation and manipulation of real-world domain concepts"
homepage := Some(url("https://github.com/shuwariafrica/world"))
startYear := Some(2023)
semanticdbEnabled := true
scmInfo := ScmInfo(
  url("https://dev.shuwari.africa/world"),
  "scm:git:https://github.com/shuwariafrica/world.git",
  Some("scm:git:git@github.com:shuwariafrica/world.git")
).some
apacheLicensed
Shuwari.organisationSettings
formattingSettings

val `world-common` =
  projectMatrix
    .in(file("modules/common"))
    .jvmPlatform(Seq(Dependencies.scalaVersion))
    .jsPlatform(Seq(Dependencies.scalaVersion))
    .nativePlatform(Seq(Dependencies.scalaVersion), nativeSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Dependencies.boilerplate)
    .settings(libraryDependencies += Dependencies.`munit-scalacheck` % Test)

val `world-locale` =
  projectMatrix
    .in(file("modules/locale"))
    .jvmPlatform(Seq(Dependencies.scalaVersion))
    .jsPlatform(Seq(Dependencies.scalaVersion))
    .nativePlatform(Seq(Dependencies.scalaVersion), nativeSettings)
    .dependsOn(`world-common`)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Dependencies.`munit-scalacheck` % Test)
    .settings(Compile / sourceGenerators += SourceGenerators.countriesGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.languagesGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.scriptsGeneratorTask)
    .settings(Compile / sourceGenerators += SourceGenerators.likelySubtagsGeneratorTask)

val `world-money` =
  projectMatrix
    .in(file("modules/money"))
    .jvmPlatform(Seq(Dependencies.scalaVersion))
    .jsPlatform(Seq(Dependencies.scalaVersion), javaTimeDependencySetting)
    .nativePlatform(Seq(Dependencies.scalaVersion), javaTimeDependencySetting ++ nativeSettings)
    .dependsOn(`world-common`)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Dependencies.`munit-scalacheck` % Test)
    .settings(Compile / sourceGenerators += SourceGenerators.currenciesGeneratorTask)

val `world-money-usage` =
  projectMatrix
    .in(file("modules/money-usage"))
    .jvmPlatform(Seq(Dependencies.scalaVersion))
    .jsPlatform(Seq(Dependencies.scalaVersion))
    .nativePlatform(Seq(Dependencies.scalaVersion), nativeSettings)
    .in(file("modules/money-usage"))
    .dependsOn(`world-locale`, `world-money`)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(libraryDependencies += Dependencies.`munit-scalacheck` % Test)
    .settings(Compile / sourceGenerators += SourceGenerators.currencyUsageGeneratorTask)

val `world-site` =
  project
    .in(file("docs"))
    .notPublished
    .enablePlugins(WorldUnidocPlugin)
    .settings(
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
        `world-common`.jvm(Dependencies.scalaVersion),
        `world-locale`.jvm(Dependencies.scalaVersion),
        `world-money`.jvm(Dependencies.scalaVersion),
        `world-money-usage`.jvm(Dependencies.scalaVersion)
      )
    )

val `world` =
  projectMatrix
    .in(file("."))
    .jvmPlatform(Seq(Dependencies.scalaVersion))
    .jsPlatform(Seq(Dependencies.scalaVersion))
    .nativePlatform(Seq(Dependencies.scalaVersion))
    .settings(publish / skip := true)
    .aggregate(
      `world-common`,
      `world-locale`,
      `world-money`,
      `world-money-usage`
    )

def javaTimeDependencySetting = List(
  libraryDependencies += Dependencies.`scala-java-time` % Provided,
  libraryDependencies += Dependencies.`scala-java-time-tzdb` % Provided
)

def nativeSettings = List(
  libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_3" % VersionScheme.Always
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += Dependencies.munit % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

def formattingSettings =
  List(
    scalafmtDetailedError := true,
    scalafmtPrintDiff := true
  )

def publishSettings = List(
  packageOptions += Package.ManifestAttributes(
    "Created-By" -> "Simple Build Tool",
    "Build-Jdk" -> System.getProperty("java.version"),
    "Specification-Title" -> name.value,
    "Specification-Version" -> Keys.version.value,
    "Specification-Vendor" -> organizationName.value
  ),
  publishTo := {
    if (isSnapshot.value)
      Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
    else localStaging.value
  },
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
