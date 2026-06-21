import sbt.*

import java.time.Instant

/** Generates Scripts.scala from CLDR data.
  *
  * Sources:
  *   - `data/cldr/common/validity/script.xml` (valid script codes)
  *   - `data/cldr/common/main/en.xml` (English script names)
  */
object ScriptsGenerator:

  def generate(cldrDir: File, targetDir: File, log: Logger): (String, File) =
    val targetFile = targetDir / "Scripts.scala"
    val scripts = CldrParser.parseScripts(cldrDir, log)
    log.info(s"ScriptsGenerator: Parsed ${scripts.size} scripts from CLDR.")
    val source = generateSource(scripts)
    (source, targetFile)

  private def generateSource(scripts: Seq[CldrParser.ScriptData]): String =
    val sb = new StringBuilder()
    sb.append(s"""// DO NOT EDIT - Generated from CLDR by ScriptsGenerator.scala at ${Instant.now}.
package world.locale.script

/** A sealed trait representing a writing system. */
sealed trait Script extends Product with Serializable derives CanEqual:
  val name: String
  val code: ScriptCode

/** Predefined singleton instances for all ISO 15924 scripts.
  *
  * Generated from CLDR data.
  */
object Scripts:

""")

    scripts.foreach { s =>
      sb.append(s"""  /** A singleton instance of [[Script]] for '''${s.name}'''. */
  case object ${s.code} extends Script:
    val name: String = ${CldrParser.escapeScalaString(s.name)}
    val code: ScriptCode = ScriptCode("${s.code}")

""")
    }

    val identifiers = scripts.map(_.code).mkString(",\n    ")
    sb.append(s"""  /** A `Set` containing all defined [[Script]] instances in this object. */
  val all: Set[Script] = Set(\n    $identifiers\n  )

  import scala.annotation.targetName

  def from(code: ScriptCode): Option[Script] = byCode.get(ScriptCode.unwrap(code))
  @targetName("fromString") def from(value: String): Option[Script] =
    ScriptCode.from(value).toOption.flatMap(c => byCode.get(ScriptCode.unwrap(c)))

  private lazy val byCode: Map[String, Script] = all.map(s => ScriptCode.unwrap(s.code) -> s).toMap

end Scripts
""")
    sb.toString()
  end generateSource
end ScriptsGenerator
