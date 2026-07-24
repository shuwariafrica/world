import org.scalajs.sbtplugin.ScalaJSCrossVersion
import scala.scalanative.sbtplugin.ScalaNativeCrossVersion

ThisBuild / organization := "africa.shuwari"
ThisBuild / description := "Scala toolkit for representation and manipulation of real-world domain concepts"
ThisBuild / homepage := Some(uri("https://github.com/shuwariafrica/world"))
ThisBuild / startYear := Some(2023)
ThisBuild / semanticdbEnabled := true
ThisBuild / scmInfo := Some(
  ScmInfo(
    uri("https://dev.shuwari.africa/world"),
    "scm:git:https://github.com/shuwariafrica/world.git",
    Some("scm:git:git@github.com:shuwariafrica/world.git")
  )
)

apacheLicensed
Shuwari.organisationSettings
inThisBuild(Compat.buildSettings)

val scala3 = Dependencies.scalaVersion

/* Core */
lazy val world = worldModule("world")

lazy val `world-money` = worldModule("world-money").dependsOn(world)

lazy val `world-quantity` = worldModule("world-quantity").dependsOn(world, `world-money`)

lazy val `world-id` = worldModule("world-id").dependsOn(world)

lazy val `world-address` = worldModule("world-address").dependsOn(world)

lazy val `world-gs1` = worldModule("world-gs1").dependsOn(world, `world-money`, `world-quantity`)

lazy val `world-party` = worldModule("world-party").dependsOn(world, `world-id`, `world-address`)

lazy val `world-temporal` = worldModule("world-temporal").dependsOn(world)

lazy val `world-text` =
  worldModule("world-text").dependsOn(world, `world-money`, `world-quantity`, `world-address`, `world-party`)

/* Curated build-time dataset. Never on a runtime classpath - consumers reach it through
 * sbt-world's hidden configuration. */
lazy val `world-data` =
  ProjectMatrix("world-data", file("modules/world-data"), getClass.getClassLoader)
    .jvmPlatform(Seq(scala3), Compat.moduleSettings(CrossVersion.binary))
    .settings(strictSettings)
    .settings(publishSettings)

/* sbt 2.x plugin. Its Scala version is sbt's own, so the module set's target does not apply. */
lazy val `sbt-world` =
  project
    .in(file("modules/sbt-world"))
    .enablePlugins(SbtPlugin)
    .settings(publishSettings)

lazy val runtimeModules: Seq[ProjectMatrix] =
  Seq(
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

lazy val `world-site` =
  project
    .in(file("docs"))
    .notPublished
    .enablePlugins(WorldUnidocPlugin)
    .dependsOn(runtimeModules.map(m => (m.jvm(scala3): ProjectReference): ClasspathDep[ProjectReference]) *)
    .settings(
      scalaVersion := scala3,
      ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(runtimeModules.map(_.jvm(scala3): ProjectReference) *)
    )

lazy val root =
  project
    .in(file("."))
    .notPublished
    .aggregate(
      (runtimeModules.flatMap(_.projectRefs) ++ `world-data`.projectRefs ++ Seq[ProjectReference](`sbt-world`)) *
    )

def worldModule(id: String): ProjectMatrix =
  ProjectMatrix(id, file(s"modules/$id"), getClass.getClassLoader)
    .jvmPlatform(Seq(scala3), Compat.moduleSettings(CrossVersion.binary))
    .jsPlatform(Seq(scala3), Compat.moduleSettings(ScalaJSCrossVersion.binary))
    .nativePlatform(Seq(scala3), nativeSettings ++ Compat.moduleSettings(ScalaNativeCrossVersion.binary))
    .settings(strictSettings)
    .settings(unitTestSettings)
    .settings(publishSettings)

/* The compiler bar. Every module builds clean under it. */
def strictSettings: List[Setting[?]] = List(
  scalacOptions ++= Seq(
    "-Yexplicit-nulls",
    "-Wunused:all",
    "-Wall",
    "-Wsafe-init",
    // -Werror is the current spelling; 3.9 deprecates the -Xfatal-warnings alias it replaces.
    "-Werror",
    "-language:strictEquality"
  )
)

def nativeSettings = List(
  libraryDependencySchemes += "org.scala-native" % "test-interface_native0.5_3" % VersionScheme.Always
)

def unitTestSettings: List[Setting[?]] = List(
  libraryDependencies += Dependencies.munit % Test,
  testFrameworks += new TestFramework("munit.Framework")
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
  publishMavenStyle := true,
  scalafmtDetailedError := true,
  scalafmtPrintDiff := true
)

def platformAlias(suffix: String, task: String, rows: Seq[Project]): Seq[Setting[?]] =
  addCommandAlias(suffix, rows.map(p => s"${p.id}/$task").mkString("; "))

platformAlias("testJVM", "test", runtimeModules.map(_.jvm(scala3)) :+ `world-data`.jvm(scala3))
platformAlias("testJS", "test", runtimeModules.map(_.js(scala3)))
platformAlias("testNative", "test", runtimeModules.map(_.native(scala3)))

addCommandAlias("fmt", "scalafmtAll; scalafmtSbt; headerCreateAll")
addCommandAlias("fmtCheck", "scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
addCommandAlias("lint", "scalafixAll --check")
