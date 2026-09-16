import sbt.*
import sbt.Keys.resolvers

// The sbt-scala-native snapshot pin resolves its runtime artifacts (nativelib and kin) at its own
// version, so the proper build needs the repository the metabuild does. Retires with the pin.
object Snapshots extends AutoPlugin:
  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[?]] = Seq(
    resolvers += "central-snapshots".at("https://central.sonatype.com/repository/maven-snapshots/")
  )
