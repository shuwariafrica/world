import scala.jdk.CollectionConverters.*
import scala.scalanative.sbtplugin.ScalaNativeCrossVersion

import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport.*
import org.scalajs.sbtplugin.ScalaJSCrossVersion
import sbt.*
import sbt.Keys.*

// Every failure path here reports and succeeds by design: compatibility is awareness before
// 1.0.0, never a gate that declines an improvement.
object Compat:

  val compatBaselines =
    settingKey[Seq[String]]("Published versions each module is reported against. Empty until the first release.")

  val compatReport =
    taskKey[Unit]("Print this module's MiMa and TASTy-MiMa compatibility report. Always succeeds.")

  def settings: List[Setting[?]] = List(
    compatBaselines := Nil,
    // A matrix row's own crossVersion reads as plain binary, so a JS or Native row would
    // otherwise resolve the JVM baseline.
    mimaPreviousArtifacts := {
      val platform = crossVersion(virtualAxes.value)
      compatBaselines.value.map(v => (organization.value % moduleName.value % v).cross(platform)).toSet
    },
    // Reporting is a side effect over transient inputs, so it opts out of the build cache.
    compatReport := Def.uncached {
      val log = streams.value.log
      val row = thisProject.value.id

      if mimaPreviousArtifacts.value.isEmpty then log.info(s"[compat] $row: no published baseline.")
      else
        val converter = fileConverter.value
        val classpath = (Compile / dependencyClasspath).value.map(entry => converter.toPath(entry.data))
        val current = mimaCurrentClassfiles.value.toPath

        mimaFindBinaryIssues.value.foreach { case (baseline, (backward, forward)) =>
          log.info(s"[compat] $row vs $baseline - MiMa: ${backward.size} backward, ${forward.size} forward")
          (backward ++ forward).foreach(problem => log.info(s"[compat]   ${problem.description("current")}"))
        }

        mimaPreviousClassfiles.value.foreach { case (baseline, previous) =>
          tastyProblems(classpath, previous.toPath, current) match
            case scala.util.Success(problems) =>
              log.info(s"[compat] $row vs $baseline - TASTy-MiMa: ${problems.size} problems")
              problems.foreach(problem => log.info(s"[compat]   ${problem.getDescription}"))
            case scala.util.Failure(error) =>
              // No published tasty-query reads 3.9 TASTy, so this is the expected branch at the
              // current target rather than a defect.
              log.warn(s"[compat] $row vs $baseline - TASTy-MiMa could not analyse: $error")
        }
      end if
    }
  )

  private def crossVersion(axes: Seq[VirtualAxis]): CrossVersion =
    if axes.contains(VirtualAxis.js) then ScalaJSCrossVersion.binary
    else if axes.contains(VirtualAxis.native) then ScalaNativeCrossVersion.binary
    else CrossVersion.binary

  private def tastyProblems
      (
        classpath: Seq[java.nio.file.Path],
        previous: java.nio.file.Path,
        current: java.nio.file.Path
      ): scala.util.Try[List[tastymima.intf.Problem]] =
    scala.util.Try {
      // Each analysed entry must itself be an element of the classpath it is read against.
      val before = (classpath :+ previous).asJava
      val after = (classpath :+ current).asJava
      new tastymima.TastyMiMa(new tastymima.intf.Config).analyze(before, previous, after, current).asScala.toList
    }

end Compat
