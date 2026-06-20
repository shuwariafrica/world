import sbt.*

import java.time.Instant

/** Generates LikelySubtags.scala from CLDR data.
  *
  * Source: `data/cldr/common/supplemental/likelySubtags.xml`
  */
object LikelySubtagsGenerator {

  def generate(cldrDir: File, targetDir: File, log: Logger): (String, File) = {
    val targetFile = targetDir / "LikelySubtags.scala"
    val subtags = CldrParser.parseLikelySubtags(cldrDir)
    log.info(s"LikelySubtagsGenerator: Parsed ${subtags.size} likely subtag mappings from CLDR.")
    val source = generateSource(subtags)
    (source, targetFile)
  }

  private def generateSource(subtags: Seq[CldrParser.LikelySubtag]): String = {
    // Split into chunks to avoid JVM 64KB method limit on <clinit>
    val chunkSize = 500
    val chunks = subtags.grouped(chunkSize).toSeq

    val sb = new StringBuilder()
    sb.append(s"""// DO NOT EDIT - Generated from CLDR by LikelySubtagsGenerator.scala at ${Instant.now}.
package world.locale

/** Internal CLDR likely-subtags table, consumed by [[Locale]] maximise/minimise. */
private[locale] object LikelySubtags:

  /** Resolves a partial locale tag to its maximised form, or `None`. */
  def resolve(tag: String): Option[String] = table.get(tag.replace('-', '_'))

""")

    chunks.zipWithIndex.foreach { case (chunk, i) =>
      sb.append(s"  private def chunk$i: Map[String, String] = Map(\n")
      chunk.foreach { s =>
        sb.append(s"""    "${s.from}" -> "${s.to.replace('_', '-')}",\n""")
      }
      sb.append("  )\n\n")
    }

    val chunkRefs = (0 until chunks.size).map(i => s"chunk$i").mkString(" ++ ")
    sb.append(s"  private lazy val table: Map[String, String] = $chunkRefs\n\n")

    sb.append(s"""end LikelySubtags
""")
    sb.toString()
  }
}
