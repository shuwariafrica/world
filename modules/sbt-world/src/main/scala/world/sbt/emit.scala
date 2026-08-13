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

// Writes resolved locales as the Scala source a build compiles.
//
// Output is byte-stable by construction: every collection is emitted in a sorted order, no
// timestamp or environment value reaches the text, and the same corpus and declaration always
// produce the same bytes - which is what makes the content-hash cache and the build cache honest.
private[sbt] object Emit:

  // Invisible characters never sit as literals in generated source any more than in written source:
  // a reader of the emitted file must be able to see that a no-break space or a bidi mark is there.
  private def invisible(char: Char): Boolean =
    val code = char.toInt
    code == 0x00a0 || code == 0x202f || code == 0x2009 || code == 0x2007 || code == 0x200e ||
    code == 0x200f || code == 0x061c || code == 0x2066 || code == 0x2067 || code == 0x2068 ||
    code == 0x2069 || code == 0x200b || code == 0xfeff || code == 0x00ad

  private def literal(text: String): String =
    val body = text.map {
      case '"'                       => "\\\""
      case '\\'                      => "\\\\"
      case '\n'                      => "\\n"
      case '\t'                      => "\\t"
      case '\r'                      => "\\r"
      case char if invisible(char)   => f"\\u${char.toInt}%04X"
      case char if char.toInt < 0x20 => f"\\u${char.toInt}%04X"
      case char                      => char.toString
    }
    body.mkString("\"", "", "\"")
  end literal

  private def part(value: Part): String = s"Part(Part.Kind.${value.kind.toString}, ${literal(value.text)})"

  private def parts(values: Vector[Part]): String =
    if values.isEmpty then "Vector.empty" else values.map(part).mkString("Vector(", ", ", ")")

  private def affix(value: Affix): String =
    if value.prefix.isEmpty && value.suffix.isEmpty then "Affix.none"
    else s"Affix(${parts(value.prefix)}, ${parts(value.suffix)})"

  private def affixes(value: Affixes): String = s"Affixes(${affix(value.positive)}, ${affix(value.negative)})"

  private def format(value: Format): String =
    s"Format(${value.primary.toString}, ${value.secondary.toString}, ${affixes(value.affixes)})"

  // A culture's display-name maps run to hundreds of entries, and a class initialiser carrying them
  // all inline exceeds the JVM's 64KB method limit. Each map is therefore built by its own methods,
  // in chunks small enough that none of them can approach that ceiling.
  private val chunk = 100

  private def rendered(pairs: Vector[(String, String)], key: String => String): Vector[String] =
    pairs.map((code, text) => s"${key(code)} -> ${literal(text)}")

  private def mapped(name: String, kind: String, values: Vector[String]): (String, Vector[String]) =
    if values.isEmpty then ("Map.empty", Vector.empty)
    else if values.sizeIs <= chunk then (values.mkString("Map(", ", ", ")"), Vector.empty)
    else
      val groups = values.grouped(chunk).toVector.zipWithIndex
      val parts = groups.map((group, at) => s"  private def $name${at.toString}: $kind = ${group.mkString("Map(", ", ", ")")}")
      val joined = groups.map((_, at) => s"$name${at.toString}").mkString(" ++ ")
      (s"$name", parts :+ s"  private def $name: $kind = $joined")

  private def texts(values: Vector[String]): String =
    if values.isEmpty then "Vector.empty" else values.map(literal).mkString("Vector(", ", ", ")")

  private def styled(owner: String, styles: Vector[(String, String)]): String =
    if styles.isEmpty then "Map.empty"
    else styles.map((style, text) => s"      $owner.$style -> ${literal(text)}").mkString("Map(\n", ",\n", ")")

  private def patterns(value: String): String =
    val four = if value.isEmpty then Vector("{0} {1}", "{0}, {1}", "{0}, {1}", "{0} {1}") else value.split("\\|", -1).toVector
    s"Culture.Data.Patterns(${four.take(4).map(literal).mkString(", ")})"

  private def field(resolved: Resolved, name: String): String = resolved.fields.getOrElse(name, "")

  private def vector(resolved: Resolved, name: String): Vector[String] =
    val value = field(resolved, name)
    if value.isEmpty then Vector.empty else value.split("\\|", -1).toVector

  private def dates(resolved: Resolved, prefix: String): Vector[(String, String)] =
    Vector("Full", "Long", "Medium", "Short")
      .map(style => style -> field(resolved, s"$prefix$style"))
      .filter((_, text) => text.nonEmpty)

  private def times(resolved: Resolved): Vector[(String, String)] =
    Vector("Medium", "Short").map(style => style -> field(resolved, s"time$style")).filter((_, text) => text.nonEmpty)

  private def monetary(resolved: Resolved): String =
    val unit = field(resolved, "currencyUnit")
    s"""Monetary(
       |      ${resolved.monetary.primary.toString},
       |      ${resolved.monetary.secondary.toString},
       |      Monetary.Form(${affixes(resolved.monetary.affixes)}, ${affixes(resolved.monetaryAlpha.affixes)}),
       |      Monetary.Form(${affixes(resolved.accounting.affixes)}, ${affixes(resolved.accountingAlpha.affixes)}),
       |      ${literal(if unit.isEmpty then "{0} {1}" else unit)}
       |    )""".stripMargin

  private def nameRules(resolved: Resolved): String =
    val surnameFirst = vector(resolved, "nameSurnameFirst")
    val set = if surnameFirst.isEmpty then "Set.empty" else surnameFirst.map(literal).mkString("Set(", ", ", ")")
    s"""Culture.Data.NameRules(
       |      formal = ${literal(field(resolved, "nameFormal"))},
       |      formalSurnameFirst = ${literal(field(resolved, "nameFormalSurnameFirst"))},
       |      informal = ${literal(field(resolved, "nameInformal"))},
       |      sorting = ${literal(field(resolved, "nameSorting"))},
       |      surnameFirst = $set
       |    )""".stripMargin

  private def numbering(values: Numbering): String =
    s"Numbering(${literal(values.digits)}, ${literal(values.decimal)}, ${literal(values.group)}, " +
      s"${values.minimum.toString}, ${literal(values.minus)}, ${literal(values.plus)}, " +
      s"${literal(values.percent)}, ${literal(values.perMille)})"

  // The data record of one resolved locale, and the methods that build its larger maps.
  private def data(resolved: Resolved): (String, Vector[String]) =
    // The active system's own separators are the locale's, and a `u-nu` selection makes a declared
    // alternate the active one.
    val active = resolved.numberings
      .find(_._1 == resolved.active)
      .map(_._2)
      .getOrElse
        (
          Numbering
            (
              resolved.digits,
              field(resolved, "decimal"),
              field(resolved, "group"),
              field(resolved, "minimum").toIntOption.getOrElse(1),
              field(resolved, "minusSign"),
              field(resolved, "plusSign"),
              field(resolved, "percentSign"),
              field(resolved, "perMille")
            )
        )
    val name = member(resolved.tag)
    val declared = resolved.numberings.map
      ((system, values) => s"${literal(system)} -> ${numbering(values)}")
    val (systems, systemParts) = mapped(s"${name}Numberings", "Map[String, Numbering]", declared)
    val (symbols, symbolParts) =
      mapped(s"${name}Symbols", "Map[Currency, String]", rendered(resolved.symbols, code => s"Currency.$code"))
    val (genericNames, nameParts) =
      mapped(s"${name}CurrencyNames", "Map[Currency, String]", rendered(resolved.currencyNames, code => s"Currency.$code"))
    val (counted, countedParts) = mapped(s"${name}Currencies", "Map[Currency, Map[Plural, String]]", currencies(resolved))
    val (territories, territoryParts) =
      mapped(s"${name}Territories", "Map[Territory, String]", rendered(resolved.territories, code => s"Territory.$code"))
    val (languages, languageParts) =
      mapped(s"${name}Languages", "Map[Language, String]", rendered(resolved.languages, code => s"Language.${identifier(code)}"))
    val (scripts, scriptParts) =
      mapped(s"${name}Scripts", "Map[Script, String]", rendered(resolved.scripts, code => s"Script.$code"))
    val record =
      s"""  val $name: Culture.Data = Culture.Data(
         |    direction = Direction.${resolved.direction},
         |    numbering = ${numbering(active)},
         |    numberings = $systems,
         |    decimal = ${format(resolved.decimal)},
         |    percent = ${format(resolved.percent)},
         |    monetary = ${monetary(resolved)},
         |    symbols = $symbols,
         |    currencies = $counted,
         |    currencyNames = $genericNames,
         |    cardinal = ${lambda(resolved.cardinal)},
         |    ordinalRule = ${lambda(resolved.ordinal)},
         |    territories = $territories,
         |    languages = $languages,
         |    scripts = $scripts,
         |    measures = Map.empty,
         |    listAnd = ${patterns(field(resolved, "listAnd"))},
         |    listOr = ${patterns(field(resolved, "listOr"))},
         |    calendar = Calendar.${resolved.calendar},
         |    dates = ${styled("DateStyle", dates(resolved, "date"))},
         |    dateTimes = ${styled("DateStyle", dates(resolved, "dateTime"))},
         |    dateTimesAt = ${styled("DateStyle", dates(resolved, "dateTimeAt"))},
         |    months = ${texts(vector(resolved, "months"))},
         |    monthsShort = ${texts(vector(resolved, "monthsShort"))},
         |    monthsStandalone = ${texts(vector(resolved, "monthsStandalone"))},
         |    days = ${texts(vector(resolved, "days"))},
         |    times = ${styled("TimeStyle", times(resolved))},
         |    dayPeriods = DayPeriods(${literal(field(resolved, "am"))}, ${literal(field(resolved, "pm"))}),
         |    names = ${nameRules(resolved)}
         |  )""".stripMargin
    (record, systemParts ++ symbolParts ++ nameParts ++ countedParts ++ territoryParts ++ languageParts ++ scriptParts)
  end data

  private def currencies(resolved: Resolved): Vector[String] =
    resolved.currencies.map
      ((code, plurals) =>
        val inner = plurals.map((count, text) => s"Plural.${count.capitalize} -> ${literal(text)}").mkString(", ")
        s"Currency.$code -> Map($inner)")

  private def lambda(source: String): String = source.linesIterator.mkString("\n      ")

  private val keywords = Set("do", "if", "then", "else", "type", "val", "var", "new", "for", "end", "as")

  private def identifier(code: String): String = if keywords.contains(code) then s"`$code`" else code

  // The value name one declared locale takes in the generated object.
  def member(tag: String): String = identifier(tag.replace('-', '_'))

  private def locale(resolved: Resolved): Either[Fault, String] =
    resolved.tag.split("-").toVector match
      case Vector(language)                                                           => Right(s"Locale(Language.${identifier(language)})")
      case Vector(language, region) if region.length == 2 && region.forall(_.isUpper) =>
        Right(s"Locale(Language.${identifier(language)}, Territory.$region)")
      case _ =>
        Left(Fault.Tag(resolved.tag, "a shape beyond the language and language-region forms the generator emits"))

  // The whole generated file: one culture per declared locale, a total negotiation entry point, and
  // nothing else.
  def apply(pack: String, resolved: Vector[Resolved], default: String): Either[Fault, String] =
    val locales = resolved.map(one => locale(one).map(one -> _))
    locales.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        =>
        val built = locales.collect { case Right(pair) => pair }
        val values = built
          .map((one, tag) => s"  val ${member(one.tag)}: Culture = Culture($tag, data.${member(one.tag)})")
          .mkString("\n")
        val all = built.map((one, _) => member(one.tag)).mkString("Vector(", ", ", ")")
        Right
          (
            s"""package $pack
               |
               |// Generated by sbt-world from world's curated presentation corpus. Edit `worldLocales` and
               |// regenerate instead: every value here is compiled from the corpus, including the affix parts
               |// that carry each locale's signs, symbols, gaps, and invisible bidi marks.
               |
               |import world.*
               |import world.text.*
               |import world.text.Culture.Data.Affix
               |import world.text.Culture.Data.Affixes
               |import world.text.Culture.Data.DayPeriods
               |import world.text.Culture.Data.Format
               |import world.text.Culture.Data.Monetary
               |import world.text.Culture.Data.Numbering
               |
               |object Cultures:
               |$values
               |
               |  val all: Vector[Culture] = $all
               |
               |  val default: Culture = ${member(default)}
               |
               |  /** Total: an unmatched preference lands on the declared default, never an Option. */
               |  def negotiate(preferences: String): Culture =
               |    Locale.negotiate(preferences, all.map(_.locale))
               |      .flatMap(chosen => all.find(_.locale == chosen))
               |      .getOrElse(default)
               |
               |private object data:
               |${built.map((one, _) => data(one)._1).mkString("\n\n")}
               |
               |${built.flatMap((one, _) => data(one)._2).mkString("\n")}
               |""".stripMargin
          )
    end match
  end apply
end Emit
