import scala.jdk.CollectionConverters.*

import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

/** Change-awareness reporting over MiMa (bytecode) and TASTy-MiMa (source/TASTy).
  *
  * Nothing here can decline a release: the report prints and the task succeeds. Compatibility is
  * never a gate before 1.0.0.
  */
object Compat:

  val compatPreviousVersions =
    settingKey[Seq[String]]("Published versions each module is reported against. Empty until the first release.")

  val compatReport =
    taskKey[Unit]("Print the MiMa and TASTy-MiMa compatibility report for this module. Always succeeds.")

  def buildSettings: Seq[Setting[?]] = Seq(
    compatPreviousVersions := Seq.empty
  )

  /** @param platform
    *   the cross-version of the row's own published artefact. A matrix row's `crossVersion` and
    *   `projectID` both read as plain binary, so the platform suffix has to be supplied by the
    *   platform block that knows it; without this a JS or Native row resolves the JVM baseline.
    */
  def moduleSettings(platform: CrossVersion): Seq[Setting[?]] = Seq(
    mimaPreviousArtifacts := compatPreviousVersions.value
      .map(version => (organization.value % moduleName.value % version).cross(platform))
      .toSet,
    // Reporting is a side effect over transient inputs, so it opts out of the build cache.
    compatReport := Def.uncached {
      val log = streams.value.log
      val row = thisProject.value.id
      val baselines = mimaPreviousArtifacts.value

      if baselines.isEmpty then log.info(s"[compat] $row: no published baseline - nothing to compare.")
      else
        val converter = fileConverter.value
        val dependencies = (Compile / dependencyClasspath).value.map(entry => converter.toPath(entry.data))

        mimaFindBinaryIssues.value.foreach { case (baseline, (backward, forward)) =>
          log.info(s"[compat] $row vs $baseline - MiMa: ${backward.size} backward, ${forward.size} forward")
          (backward ++ forward).foreach(problem => log.info(s"[compat]   ${problem.description("current")}"))
        }

        // TASTy-MiMa reads packaged artefacts: a classes directory is not indexed the same way.
        val current = converter.toPath((Compile / packageBin).value)
        mimaPreviousClassfiles.value.foreach { case (baseline, previous) =>
          tastyProblems(dependencies, previous.toPath, current) match
            case scala.util.Success(problems) =>
              log.info(s"[compat] $row vs $baseline - TASTy-MiMa: ${problems.size} problems")
              problems.foreach(problem => log.info(s"[compat]   ${problem.getDescription}"))
            case scala.util.Failure(error) =>
              // Reporting is awareness, not a gate: an engine that cannot read the artefact says
              // so and the build carries on. No published tasty-query reads Scala 3.9 TASTy yet,
              // so this branch is the expected one at the current target.
              log.warn(s"[compat] $row vs $baseline - TASTy-MiMa could not analyse: $error")
        }
      end if
    }
  )

  private def tastyProblems
      (
        dependencies: Seq[java.nio.file.Path],
        previous: java.nio.file.Path,
        current: java.nio.file.Path
      ): scala.util.Try[List[tastymima.intf.Problem]] =
    scala.util.Try {
      // Each analysed entry must itself be an element of the classpath it is read against.
      val before = (dependencies :+ previous).asJava
      val after = (dependencies :+ current).asJava
      new tastymima.TastyMiMa(new tastymima.intf.Config).analyze(before, previous, after, current).asScala.toList
    }

end Compat
