val libraries = new {
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.1.0")
  val `munit-scalacheck` = munit(_.withName("munit-scalacheck"))
  val `scala-java-time` = Def.setting("io.github.cquiroz" %%% "scala-java-time" % "2.6.0")
  val `scala-java-time-tzdb` = `scala-java-time`.apply(_.withName("scala-java-time-tzdb"))
}

val `world-common` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/common"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))

val `world-locale` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/locale"))
    .dependsOn(`world-common`)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += SourceGenerators.countriesGeneratorTask)

val `world-money` =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/money"))
    .dependsOn(`world-locale`)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += SourceGenerators.currenciesGeneratorTask)
    .jsSettings(libraryDependency(libraries.`scala-java-time`(_ % Provided)))
    .jsSettings(libraryDependency(libraries.`scala-java-time-tzdb`(_ % Provided)))
    .nativeSettings(libraryDependency(libraries.`scala-java-time`(_ % Provided)))
    .nativeSettings(libraryDependency(libraries.`scala-java-time-tzdb`(_ % Provided)))

val `world-jvm` =
  project
    .in(file(".jvm"))
    .notPublished
    .aggregate(
      `world-common`.jvm,
      `world-locale`.jvm,
      `world-money`.jvm
    )

val `world-native` =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(
      `world-common`.native,
      `world-locale`.native,
      `world-money`.native
    )

val `world-js` =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(
      `world-common`.js,
      `world-locale`.js,
      `world-money`.js
    )

val `world-root` =
  project
    .in(file("."))
    .shuwariProject
    .notPublished
    .apacheLicensed
    .enablePlugins(ScalaUnidocPlugin, WorldUnidocPlugin)
    .aggregate(`world-jvm`, `world-js`, `world-native`)
    .settings(sonatypeProfileSetting)
    .settings(
      // Aggregate ScalaUnidoc from JVM projects only
      ScalaUnidoc / unidoc / unidocProjectFilter := inAnyProject -- inProjects(`world-js`, `world-native`)
    )

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

def publishCredentials = credentials := List(
  Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    System.getenv("PUBLISH_USER"),
    System.getenv("PUBLISH_USER_PASSPHRASE")
  )
)

def publishSettings = publishCredentials +: pgpSettings ++: List(
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
  publishTo := sonatypePublishToBundle.value,
  pomIncludeRepository := (_ => false),
  publishMavenStyle := true,
  sonatypeProfileSetting
)

def sonatypeProfileSetting = sonatypeProfileName := "africa.shuwari"

def pgpSettings = List(
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

addCommandAlias("format", "project world-jvm; scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("analyse", "project world-jvm; scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
