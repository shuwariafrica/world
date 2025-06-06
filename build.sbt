val libraries = new {
  val munit = Def.setting("org.scalameta" %%% "munit" % "1.1.0")
  val `munit-scalacheck` = munit(_.withName("munit-scalacheck"))
}

val showInfo = taskKey[Unit]("Show info")

val locale =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/locale"))
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += CountriesPopulator.generator)

val money =
  crossProject(JVMPlatform, JSPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .withoutSuffixFor(JVMPlatform)
    .in(file("modules/money"))
    .dependsOn(locale)
    .settings(unitTestSettings)
    .settings(publishSettings)
    .dependsOn(libraries.`munit-scalacheck`(_ % Test))
    .settings(Compile / sourceGenerators += CurrenciesPopulator.generator)

val jvmProjects =
  project
    .in(file(".jvm"))
    .notPublished
    .aggregate(locale.jvm, money.jvm)

val nativeProjects =
  project
    .in(file(".native"))
    .notPublished
    .aggregate(locale.native, money.native)

val jsProjects =
  project
    .in(file(".js"))
    .notPublished
    .aggregate(locale.js, money.js)

val `money-root` =
  project
    .in(file("."))
    .shuwariProject
    .notPublished
    .apacheLicensed
    .aggregate(jvmProjects, jsProjects, nativeProjects)
    .settings(sonatypeProfileSetting)

inThisBuild(
  List(
    scalaVersion := crossScalaVersions.value.head,
    crossScalaVersions := List("3.3.5"),
    organization := "africa.shuwari",
    description := "Scala Money & Currency API.",
    homepage := Some(url("https://github.com/shuwarifrica/money")),
    startYear := Some(2023),
    semanticdbEnabled := true,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    publishCredentials,
    scmInfo := ScmInfo(
      url("https://github.com/shuwariafrica/money"),
      "scm:git:https://github.com/shuwariafrica/money.git",
      Some("scm:git:git@github.com:shuwariafrica/money.git")
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
  PgpKeys.pgpSelectPassphrase :=
    sys.props
      .get("SIGNING_KEY_PASSPHRASE")
      .map(_.toCharArray),
  usePgpKeyHex(System.getenv("SIGNING_KEY_ID"))
)

addCommandAlias("format", "scalafixAll; scalafmtAll; scalafmtSbt; headerCreateAll")

addCommandAlias("staticCheck", "scalafixAll --check; scalafmtCheckAll; scalafmtSbtCheck; headerCheckAll")
