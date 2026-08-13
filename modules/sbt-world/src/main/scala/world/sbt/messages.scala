/****************************************************************************
 * Copyright 2023, 2026 Ali Rashid.                                         *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *     http://www.apache.org/licenses/LICENSE-2.0                           *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ****************************************************************************/
package world.sbt

// One catalogue entry: the key a translator sees, the typed signature a call site sees, and the
// source-language pattern.
final private[sbt] case class Entry(key: String, parameters: Vector[(String, String)], pattern: String) derives CanEqual:

  // The method name the key generates: dots separate the catalogue's own grouping, and the method
  // is that grouping in camel case.
  def method: String =
    val parts = key.split("\\.").toVector.filter(_.nonEmpty)
    (parts.headOption.getOrElse(key) +: parts.drop(1).map(_.capitalize)).mkString

// A translated entry: the plural-keyed forms a locale supplies for one key.
final private[sbt] case class Translation(key: String, forms: Vector[(String, String)]) derives CanEqual

// Reads the reference catalogue and the translators' PO files, and compiles both into the message
// objects a build compiles.
//
// World governs the plural mapping: a translator's PO carries CLDR category names, and the emitted
// match is exhaustive over the library's own category set, so the compiler independently
// corroborates the generator's coverage check.
private[sbt] object Messages:

  private val categories = Vector("zero", "one", "two", "few", "many", "other")

  // Parses the reference catalogue: one entry per line, `key(name: Type, ...) = pattern`, with
  // blank lines and `#` comments ignored.
  def catalogue(text: String): Either[Fault, Vector[Entry]] =
    val read = text.linesIterator.toVector.zipWithIndex
      .map((line, at) => line.trim -> at)
      .filterNot((line, _) => line.isEmpty || line.startsWith("#"))
      .map { (line, at) =>
        line.split("=", 2).toVector match
          case Vector(head, pattern) => entry(head.trim, pattern.trim)
          case _                     => Left(Fault.Catalogue(s"line ${(at + 1).toString}", "is not a `key(parameters) = pattern` entry"))
      }
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        val entries = read.collect { case Right(one) => one }
        entries.groupBy(_.key).collectFirst { case (key, all) if all.sizeIs > 1 => key } match
          case Some(key) => Left(Fault.Catalogue(key, "is declared twice"))
          case None      => Right(entries.sortBy(_.key))
  end catalogue

  private def entry(head: String, pattern: String): Either[Fault, Entry] =
    head.indexOf('(') match
      case -1 => Left(Fault.Catalogue(head, "declares no parameter list - write `()` where it takes none"))
      case at =>
        val key = head.take(at).trim
        val body = head.drop(at + 1).trim
        if !body.endsWith(")") then Left(Fault.Catalogue(key, "has an unclosed parameter list"))
        else
          val declared = body.dropRight(1).trim
          val parameters =
            if declared.isEmpty then Vector.empty
            else declared.split(",").toVector.map(_.trim)
          val parsed = parameters.map { one =>
            one.split(":", 2).toVector match
              case Vector(name, kind) => Right(name.trim -> kind.trim)
              case _                  => Left(Fault.Catalogue(key, s"declares the parameter '$one' without a type"))
          }
          parsed.collectFirst { case Left(fault) => fault } match
            case Some(fault) => Left(fault)
            case None        => Right(Entry(key, parsed.collect { case Right(pair) => pair }, pattern))
        end if

  // Reads a translator's PO file: `msgctxt` carries the catalogue key, `msgstr` the singular form,
  // and `msgstr[n]` the plural forms in the header's own category order.
  def po(text: String, order: Vector[String]): Either[Fault, Vector[Translation]] =
    val blocks = text.split("(?m)^\\s*$").toVector.map(_.trim).filter(_.nonEmpty)
    val read = blocks.filter(_.contains("msgctxt")).map { block =>
      val lines = block.linesIterator.toVector.map(_.trim)
      val key = lines.find(_.startsWith("msgctxt")).map(line => quoted(line.drop("msgctxt".length).trim))
      val plurals = lines.filter(_.startsWith("msgstr[")).map { line =>
        val index = line.drop("msgstr[".length).takeWhile(_ != ']').toIntOption.getOrElse(-1)
        index -> quoted(line.dropWhile(_ != ']').drop(1).trim)
      }
      val singular =
        lines.find(line => line.startsWith("msgstr ") || line.startsWith("msgstr\"")).map(line => quoted(line.drop("msgstr".length).trim))
      key match
        case None       => Left(Fault.Catalogue("a PO block", "carries no msgctxt naming its catalogue key"))
        case Some(name) =>
          if plurals.nonEmpty then
            val forms = plurals.map((index, text) => order.lift(index).getOrElse(s"form${index.toString}") -> text)
            Right(Translation(name, forms))
          else
            singular match
              case Some(text) => Right(Translation(name, Vector("other" -> text)))
              case None       => Left(Fault.Catalogue(name, "carries no translation"))
    }
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(one) => one })
  end po

  private def quoted(text: String): String =
    val body = text.stripPrefix("\"").stripSuffix("\"")
    body.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

  // The categories a locale's own rules select, which are the branches its translation must cover
  // and the branches the emitted match names explicitly.
  def selected(rules: Vector[(String, String)]): Vector[String] =
    val declared = rules.map(_._1).filter(categories.contains)
    (declared :+ "other").distinct.sortBy(categories.indexOf)

  // Compiles one pattern into a Scala string expression over the entry's own parameters.
  //
  // A parameter of any type renders through its `Display` instance, and a plain string is wrapped
  // in first-strong isolates in EVERY culture, because its direction is never known.
  def expression(entry: Entry, pattern: String, counted: Option[String]): Either[Fault, String] =
    @annotation.tailrec
    def scan(at: Int, out: Vector[String]): Either[Fault, Vector[String]] =
      pattern.indexOf('{', at) match
        case -1   => Right(out :+ run(pattern.substring(at), counted))
        case open =>
          val close = pattern.indexOf('}', open)
          if close < 0 then Left(Fault.Catalogue(entry.key, "has an unclosed placeholder"))
          else
            val name = pattern.substring(open + 1, close).trim
            val literal = run(pattern.substring(at, open), counted)
            entry.parameters.find(_._1 == name) match
              case None       => Left(Fault.Catalogue(entry.key, s"names the parameter '$name', which it does not declare"))
              case Some(pair) =>
                val (parameter, kind) = pair
                val rendered = if kind == "String" then s"$${culture.isolate($parameter)}" else s"$${$parameter.display}"
                scan(close + 1, out :+ literal :+ rendered)
    scan(0, Vector.empty).map(parts => s"s\"${parts.mkString}\"")
  end expression

  // Inside a plural form, a bare `#` is the formatted number itself - ICU's own token for it - so a
  // literal run carries the counted parameter's own rendering wherever it appears.
  private def run(raw: String, counted: Option[String]): String =
    counted match
      case None       => text(raw)
      case Some(name) => raw.split("#", -1).map(text).mkString(s"$${$name.display}")

  private def text(raw: String): String = raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "$$")

  // The plural block of a pattern, where it is one: the parameter it counts by and the source forms
  // per category. A catalogue entry either counts or it does not - a pattern mixing a plural block
  // with surrounding text would need a second substitution pass world's message shape does not have.
  def plural(entry: Entry): Either[Fault, Option[(String, Vector[(String, String)])]] =
    val pattern = entry.pattern.trim
    if !pattern.startsWith("{") || !pattern.contains(", plural,") then Right(None)
    else if !pattern.endsWith("}") then Left(Fault.Catalogue(entry.key, "has an unclosed plural block"))
    else
      val body = pattern.drop(1).dropRight(1)
      val name = body.takeWhile(_ != ',').trim
      val arms = body.dropWhile(_ != ',').drop(1).trim.stripPrefix("plural,").trim
      forms(entry, arms).map(read => Some(name -> read))

  private def forms(entry: Entry, arms: String): Either[Fault, Vector[(String, String)]] =
    @annotation.tailrec
    def scan(at: Int, out: Vector[(String, String)]): Either[Fault, Vector[(String, String)]] =
      val rest = arms.substring(at).trim
      if rest.isEmpty then Right(out)
      else
        val start = arms.length - rest.length
        val category = rest.takeWhile(_ != '{').trim
        val open = arms.indexOf('{', start)
        if open < 0 then Left(Fault.Catalogue(entry.key, s"declares the category '$category' with no form"))
        else
          val close = balanced(arms, open)
          if close < 0 then Left(Fault.Catalogue(entry.key, s"leaves the '$category' form unclosed"))
          else if !categories.contains(category) then Left(Fault.Catalogue(entry.key, s"names '$category', which is not a plural category"))
          else scan(close + 1, out :+ (category -> arms.substring(open + 1, close)))
    end scan
    scan(0, Vector.empty)
  end forms

  private def balanced(text: String, open: Int): Int =
    @annotation.tailrec
    def walk(at: Int, depth: Int): Int =
      if at >= text.length then -1
      else
        text.charAt(at) match
          case '{'               => walk(at + 1, depth + 1)
          case '}' if depth == 1 => at
          case '}'               => walk(at + 1, depth - 1)
          case _                 => walk(at + 1, depth)
    walk(open, 0)

  // One locale's half of the generated file: the object that supplies its culture and answers every
  // catalogue entry.
  private def locale
      (
        entries: Vector[Entry],
        member: String,
        culture: String,
        selectedCategories: Vector[String],
        translations: Map[String, Translation]
      ): Either[Fault, String] =
    val bodies = entries.map { entry =>
      translations.get(entry.key) match
        case None              => Left(Fault.Translation(member, entry.key, "is absent from this locale's catalogue"))
        case Some(translation) =>
          plural(entry).flatMap {
            case None =>
              val form = translation.forms.find(_._1 == "other").map(_._2).getOrElse("")
              expression(entry, form, None).map(body => s"  def ${entry.method}${signature(entry)}: String = $body")
            case Some((counted, _)) =>
              val missing = selectedCategories.filterNot(category => translation.forms.exists(_._1 == category))
              if missing.nonEmpty then
                Left(Fault.Translation(member, entry.key, s"covers no ${missing.mkString(", ")} form, which this locale selects"))
              else
                branches(entry, counted, selectedCategories, translation).map
                  (body => s"  def ${entry.method}${signature(entry)}: String =\n    culture.plural($counted) match\n$body")
          }
    }
    bodies.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        val text = bodies.collect { case Right(one) => one }.mkString("\n")
        Right(s"private object $member extends Messages:\n  override protected given culture: Culture = $culture\n$text")
  end locale

  private def signature(entry: Entry): String =
    if entry.parameters.isEmpty then "()"
    else entry.parameters.map((name, kind) => s"$name: $kind").mkString("(", ", ", ")")

  // The emitted match is exhaustive over the library's own category set: the categories this locale
  // selects take their own arm, and every other category joins the `other` arm, so the compiler
  // corroborates the coverage the generator checked.
  private def branches
      (entry: Entry, counted: String, selectedCategories: Vector[String], translation: Translation): Either[Fault, String] =
    val named = selectedCategories.filterNot(_ == "other")
    val rest = categories.filterNot(named.contains)
    val arms = named.map { category =>
      val form = translation.forms.find(_._1 == category).map(_._2).getOrElse("")
      expression(entry, form, Some(counted)).map(body => s"      case Plural.${category.capitalize} => $body")
    } :+ {
      val form = translation.forms.find(_._1 == "other").map(_._2).getOrElse("")
      expression(entry, form, Some(counted)).map
        (body => s"      case ${rest.map(one => s"Plural.${one.capitalize}").mkString(" | ")} => $body")
    }
    arms.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(arms.collect { case Right(one) => one }.mkString("\n"))
  end branches

  // The whole generated messages file.
  def apply
      (
        pack: String,
        entries: Vector[Entry],
        locales: Vector[(String, String, Vector[String], Map[String, Translation])],
        default: String
      ): Either[Fault, String] =
    val objects = locales.map
      ((member, culture, selectedCategories, translations) => locale(entries, member, culture, selectedCategories, translations))
    objects.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        val declarations = entries.map(entry => s"  def ${entry.method}${signature(entry)}: String").mkString("\n")
        val dispatch = locales
          .filterNot((member, _, _, _) => member == default)
          .map((member, culture, _, _) => s"    if culture == $culture then $member else")
          .mkString("\n")
        Right
          (
            s"""package $pack
               |
               |// Generated by sbt-world from the reference catalogue and its translations. Edit those and
               |// regenerate instead.
               |
               |import world.text.*
               |
               |trait Messages:
               |  // One wiring point: the trait defers the culture, each locale object provides it, and an
               |  // object that forgets does not compile.
               |  protected given culture: Culture = scala.compiletime.deferred
               |$declarations
               |
               |object Messages:
               |  /** Total dispatch over the declared locale set. */
               |  def apply(culture: Culture): Messages =
               |$dispatch
               |    $default
               |
               |${objects.collect { case Right(one) => one }.mkString("\n\n")}
               |""".stripMargin
          )
    end match
  end apply
end Messages
