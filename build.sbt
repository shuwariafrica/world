scalaVersion := Libraries.scala3
organization := "africa.shuwari"
description := "Concepts commerce runs on - civil time, places and locales, money, quantities, etc - in Scala"
homepage := Some(uri("https://github.com/shuwariafrica/world"))
startYear := Some(2023)
semanticdbEnabled := true
scmInfo := ScmInfo(
  uri("https://dev.shuwari.africa/world"),
  "scm:git:https://github.com/shuwariafrica/world.git",
  Some("scm:git:git@github.com:shuwariafrica/world.git")
).some

apacheLicensed
Shuwari.organisationSettings
formattingSettings

// Every matrix row presents the same source files to its own copy of the rewriting tasks, and two
// rows rewriting one file at once truncated it during the pass-II run. The tag is attached to the
// tasks from the outside and the limit lets only one of them write at a time.
val rewrite = Tags.Tag("rewrite")

Global / concurrentRestrictions += Tags.limit(rewrite, 1)

def rewriteSettings: List[Setting[?]] = List(
  Compile / scalafmt := (Compile / scalafmt).tag(rewrite).value,
  Test / scalafmt := (Test / scalafmt).tag(rewrite).value,
  Compile / scalafix := (Compile / scalafix).tag(rewrite).evaluated,
  Test / scalafix := (Test / scalafix).tag(rewrite).evaluated,
  Compile / headerCreate := (Compile / headerCreate).tag(rewrite).value,
  Test / headerCreate := (Test / headerCreate).tag(rewrite).value
)

val `world-core` =
  projectMatrix
    .in(file("modules/core"))
    .settings(description := "Civil time, exact rationals, calendars, and the scheme concept.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(libraryDependencies += Libraries.boilerplate)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val world =
  projectMatrix
    .in(file("modules/world"))
    .dependsOn(`world-core`)
    .settings(description := "Places, languages, scripts, locales, and currencies.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(Data.registers)
    .settings(libraryDependencies += Libraries.`boilerplate-testkit` % Test)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-money` =
  projectMatrix
    .in(file("modules/money"))
    .dependsOn(`world-core`, world)
    .settings(description := "Monetary amounts, rates, tax, and exact allocation.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(Data.monetary)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-quantity` =
  projectMatrix
    .in(file("modules/quantity"))
    .dependsOn(`world-core`, world, `world-money`)
    .settings(description := "Measurement kinds, units, quantities, unit prices, and tariffs.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-id` =
  projectMatrix
    .in(file("modules/id"))
    .dependsOn(`world-core`, world)
    .settings(description := "Bank, telephone, and internet identifiers.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(Data.identifiers)
    .settings(libraryDependencies += Libraries.`boilerplate-testkit` % Test)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-address` =
  projectMatrix
    .in(file("modules/address"))
    .dependsOn(`world-core`, world, `world-quantity`)
    .settings(description := "Structured postal addresses and geographic coordinates.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(Data.addressing)
    .settings(libraryDependencies += Libraries.`boilerplate-testkit` % Test)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-party` =
  projectMatrix
    .in(file("modules/party"))
    // The consumer-declared schemes the identifier suites verify the scheme concept through are the
    // same declarations the party seam must accept, so the party suites read them rather than
    // restating them.
    .dependsOn(`world-core`, world, `world-id` % "compile->compile;test->test", `world-address`)
    .settings(description := "Names, organisations, and the parties a document addresses.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-text` =
  projectMatrix
    .in(file("modules/text"))
    .dependsOn(
      `world-core`,
      world,
      `world-money`,
      `world-quantity`,
      `world-address`,
      `world-id` % "compile->compile;test->test",
      `world-party`
    )
    .settings(description := "Locale-correct presentation of world's values.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(Libraries.scala3))
    .jsPlatform(Seq(Libraries.scala3))
    .nativePlatform(Seq(Libraries.scala3), nativeSettings)

val `world-data` =
  projectMatrix
    .in(file("modules/data"))
    .settings(description := "Curated source data compiled into world's artefacts.")
    .settings(compilerSettings)
    .settings(rewriteSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .settings(Data.curation)
    .jvmPlatform(Seq(Libraries.scala3))

val `world-site` =
  project
    .in(file("docs"))
    .enablePlugins(WorldUnidocPlugin)
    .settings(publish / skip := true)
    .settings(rewriteSettings)
    .settings(scalaVersion := Libraries.scala3)
    .dependsOn(
      `world-core`.jvm(Libraries.scala3),
      world.jvm(Libraries.scala3),
      `world-money`.jvm(Libraries.scala3),
      `world-quantity`.jvm(Libraries.scala3),
      `world-id`.jvm(Libraries.scala3),
      `world-address`.jvm(Libraries.scala3),
      `world-party`.jvm(Libraries.scala3),
      `world-text`.jvm(Libraries.scala3)
    )
    .settings(
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
        `world-core`.jvm(Libraries.scala3),
        world.jvm(Libraries.scala3),
        `world-money`.jvm(Libraries.scala3),
        `world-quantity`.jvm(Libraries.scala3),
        `world-id`.jvm(Libraries.scala3),
        `world-address`.jvm(Libraries.scala3),
        `world-party`.jvm(Libraries.scala3),
        `world-text`.jvm(Libraries.scala3)
      )
    )

val `world-jvm` =
  projectMatrix
    .in(file(".jvm"))
    .jvmPlatform(Seq(Libraries.scala3))
    .settings(publish / skip := true)
    .aggregate(
      `world-core`,
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-party`,
      `world-text`,
      `world-data`
    )

val `world-js` =
  projectMatrix
    .in(file(".js"))
    .jsPlatform(Seq(Libraries.scala3))
    .defaultAxes(VirtualAxis.js, VirtualAxis.scalaABIVersion(Libraries.scala3))
    .settings(publish / skip := true)
    .aggregate(
      `world-core`,
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-party`,
      `world-text`
    )

val `world-native` =
  projectMatrix
    .in(file(".native"))
    .nativePlatform(Seq(Libraries.scala3))
    .defaultAxes(VirtualAxis.native, VirtualAxis.scalaABIVersion(Libraries.scala3))
    .settings(publish / skip := true)
    .aggregate(
      `world-core`,
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-party`,
      `world-text`
    )

val `world-root` =
  projectMatrix
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(`world-jvm`, `world-js`, `world-native`)

def compilerOptions = List(
  "-Yexplicit-nulls",
  "-Wunused:all",
  "-Wall",
  "-Wsafe-init",
  "-Werror",
  "-language:strictEquality"
)

def compilerSettings = List(
  Compile / compile / scalacOptions ++= compilerOptions,
  Test / compile / scalacOptions ++= compilerOptions,
  Compile / doc / scalacOptions := Nil,
  Test / doc / scalacOptions := Nil
)

def nativeSettings = List(
  libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_3" % VersionScheme.Always
)

def formattingSettings = List(
  scalafmtDetailedError := true,
  scalafmtPrintDiff := true
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += Libraries.munit % Test,
  testFrameworks += new TestFramework("munit.Framework")
)

def manifestSettings = packageOptions += Package.ManifestAttributes(
  "Build-Jdk" -> System.getProperty("java.version"),
  "Specification-Title" -> name.value,
  "Specification-Version" -> Keys.version.value,
  "Specification-Vendor" -> organizationName.value
)

def licenceSettings = List(
  Compile / resourceGenerators += Def.task {
    val root = (ThisBuild / baseDirectory).value
    val meta = (Compile / resourceManaged).value / "META-INF"
    Seq("LICENSE", "NOTICE").map { name =>
      val target = meta / name
      IO.copyFile(root / name, target)
      target
    }
  }.taskValue
)

def publishSettings = licenceSettings ++ {
  import java.time.Year
  List[Setting[?]](
    manifestSettings,
    headerLicense := {
      val start = startYear.value.get
      val current: Int = Year.now.getValue
      val developmentTimeline = if start == current then s"$current" else s"$start, $current"
      Some(HeaderLicense.ALv2(developmentTimeline, "Ali Rashid."))
    },
    headerEmptyLine := false,
    publishTo := {
      if (isSnapshot.value) Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
      else localStaging.value
    },
    pomIncludeRepository := (_ => false),
    publishMavenStyle := true
  )
}

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
