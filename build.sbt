scalaVersion := scala3
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

def scala3 = Libraries.scala3

val world =
  projectMatrix
    .in(file("modules/core"))
    .settings(description := "Places, languages, locales, currencies, and civil time.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-money` =
  projectMatrix
    .in(file("modules/money"))
    .dependsOn(world)
    .settings(description := "Monetary amounts, rates, tax, and exact allocation.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-quantity` =
  projectMatrix
    .in(file("modules/quantity"))
    .dependsOn(world, `world-money`)
    .settings(description := "Measurement kinds, units, quantities, and unit prices.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-id` =
  projectMatrix
    .in(file("modules/id"))
    .dependsOn(world)
    .settings(description := "Telephone, email, banking, tax, and card identifiers.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-address` =
  projectMatrix
    .in(file("modules/address"))
    .dependsOn(world)
    .settings(description := "Postal addresses and per-territory address rules.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-gs1` =
  projectMatrix
    .in(file("modules/gs1"))
    .dependsOn(world, `world-money`, `world-quantity`)
    .settings(description := "GTIN, GLN, SSCC, and GS1 element strings.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-party` =
  projectMatrix
    .in(file("modules/party"))
    .dependsOn(world, `world-id`, `world-address`)
    .settings(description := "Personal names, organisations, and parties.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-temporal` =
  projectMatrix
    .in(file("modules/temporal"))
    .dependsOn(world)
    .settings(description := "Instants, zones, business calendars, and fiscal periods.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-text` =
  projectMatrix
    .in(file("modules/text"))
    .dependsOn(world, `world-money`, `world-quantity`, `world-address`, `world-party`)
    .settings(description := "Cultures, locale-correct display, and the message substrate.")
    .settings(compilerSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))
    .jsPlatform(Seq(scala3))
    .nativePlatform(Seq(scala3), nativeSettings)

val `world-data` =
  projectMatrix
    .in(file("modules/data"))
    .settings(description := "Curated source data compiled into world's artefacts.")
    .settings(compilerSettings)
    .settings(publishSettings)
    .settings(Compat.settings)
    .jvmPlatform(Seq(scala3))

val `sbt-world` =
  projectMatrix
    .in(file("modules/sbt-world"))
    .enablePlugins(SbtPlugin)
    .settings(description := "Locale and zone coverage declarations, dataset slicing, and message generation.")
    .settings(publishSettings)
    .jvmPlatform(Seq(scala3))

val `world-site` =
  project
    .in(file("docs"))
    .enablePlugins(WorldUnidocPlugin)
    .settings(publish / skip := true)
    .settings(scalaVersion := scala3)
    .dependsOn(
      world.jvm(scala3),
      `world-money`.jvm(scala3),
      `world-quantity`.jvm(scala3),
      `world-id`.jvm(scala3),
      `world-address`.jvm(scala3),
      `world-gs1`.jvm(scala3),
      `world-party`.jvm(scala3),
      `world-temporal`.jvm(scala3),
      `world-text`.jvm(scala3)
    )
    .settings(
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(
        world.jvm(scala3),
        `world-money`.jvm(scala3),
        `world-quantity`.jvm(scala3),
        `world-id`.jvm(scala3),
        `world-address`.jvm(scala3),
        `world-gs1`.jvm(scala3),
        `world-party`.jvm(scala3),
        `world-temporal`.jvm(scala3),
        `world-text`.jvm(scala3)
      )
    )

val `world-jvm` =
  projectMatrix
    .in(file(".jvm"))
    .jvmPlatform(Seq(scala3))
    .settings(publish / skip := true)
    .aggregate(
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-gs1`,
      `world-party`,
      `world-temporal`,
      `world-text`,
      `world-data`,
      `sbt-world`
    )

val `world-js` =
  projectMatrix
    .in(file(".js"))
    .jsPlatform(Seq(scala3))
    .defaultAxes(VirtualAxis.js, VirtualAxis.scalaABIVersion(scala3))
    .settings(publish / skip := true)
    .aggregate(
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-gs1`,
      `world-party`,
      `world-temporal`,
      `world-text`
    )

val `world-native` =
  projectMatrix
    .in(file(".native"))
    .nativePlatform(Seq(scala3))
    .defaultAxes(VirtualAxis.native, VirtualAxis.scalaABIVersion(scala3))
    .settings(publish / skip := true)
    .aggregate(
      world,
      `world-money`,
      `world-quantity`,
      `world-id`,
      `world-address`,
      `world-gs1`,
      `world-party`,
      `world-temporal`,
      `world-text`
    )

val `world-root` =
  projectMatrix
    .in(file("."))
    .settings(publish / skip := true)
    .aggregate(`world-jvm`, `world-js`, `world-native`)

def compilerOptions = List("-Wall", "-Wsafe-init")

def compilerSettings = List(
  Compile / compile / scalacOptions ++= compilerOptions,
  Test / compile / scalacOptions ++= compilerOptions ++ List("-Yexplicit-nulls", "-language:strictEquality"),
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

def publishSettings = List(
  manifestSettings,
  publishTo := {
    if (isSnapshot.value) Some("central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/"))
    else localStaging.value
  },
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("check", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
