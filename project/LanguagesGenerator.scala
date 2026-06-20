import sbt.*

import java.time.Instant

/** Generates Languages.scala from CLDR data.
  *
  * Sources:
  *   - `data/cldr/common/main/` (locale file listing)
  *   - `data/cldr/common/supplemental/supplementalData.xml` (language-script associations)
  *   - `data/cldr/common/main/en.xml` (English language names)
  */
object LanguagesGenerator {

  def generate(cldrDir: File, targetDir: File, log: Logger): (String, File) = {
    val targetFile = targetDir / "Languages.scala"
    val languages = CldrParser.parseLanguages(cldrDir, log)
    log.info(s"LanguagesGenerator: Parsed ${languages.size} languages from CLDR.")
    val source = generateSource(languages)
    (source, targetFile)
  }

  /** Generates a valid Scala identifier from a language code.
    * Some codes start with digits or conflict with Scala keywords.
    */
  private def toIdentifier(code: String): String = {
    // Language codes are 2-3 lowercase letters, safe as Scala identifiers
    // but backtick-quote if they happen to be Scala keywords
    val keywords = Set("do", "if", "in", "is", "as", "to", "or")
    if (keywords.contains(code)) s"`$code`" else code
  }

  private def generateSource(languages: Seq[CldrParser.LanguageData]): String = {
    val sb = new StringBuilder()
    sb.append(s"""// DO NOT EDIT - Generated from CLDR by LanguagesGenerator.scala at ${Instant.now}.
package world.locale.language

import world.locale.script.ScriptCode

/** A sealed trait representing a language with its primary identifiers. */
sealed trait Language extends Product with Serializable derives CanEqual:
  val name: String
  val code: LanguageCode
  val scripts: Set[ScriptCode]

/** Predefined singleton instances for languages with CLDR locale data.
  *
  * Generated from CLDR data.
  */
object Languages:

""")

    languages.foreach { l =>
      val id = toIdentifier(l.code)
      val scriptsLiteral =
        if (l.scripts.isEmpty) "Set.empty"
        else l.scripts.toSeq.sorted.map(s => s"""ScriptCode("$s")""").mkString("Set(", ", ", ")")
      sb.append(s"""  /** A singleton instance of [[Language]] for '''${l.name}'''. */
  case object $id extends Language:
    val name: String = ${CldrParser.escapeScalaString(l.name)}
    val code: LanguageCode = LanguageCode("${l.code}")
    val scripts: Set[ScriptCode] = $scriptsLiteral

""")
    }

    val identifiers = languages.map(l => toIdentifier(l.code)).mkString(",\n    ")
    sb.append(s"""  /** A `Set` containing all defined [[Language]] instances in this object. */
  val all: Set[Language] = Set(\n    $identifiers\n  )

  import scala.annotation.targetName

  def from(code: LanguageCode): Option[Language] = byCode.get(LanguageCode.unwrap(code))
  @targetName("fromString") def from(value: String): Option[Language] =
    LanguageCode.from(value).toOption.flatMap(c => byCode.get(LanguageCode.unwrap(c)))

  private lazy val byCode: Map[String, Language] = all.map(l => LanguageCode.unwrap(l.code) -> l).toMap

end Languages
""")
    sb.toString()
  }
}
