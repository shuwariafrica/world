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

// One numbering system's own vocabulary, as the library's record carries it.
final private[sbt] case class Numbering
  (
    digits: String,
    decimal: String,
    group: String,
    minimum: Int,
    minus: String,
    plus: String,
    percent: String,
    perMille: String
  ) derives CanEqual

// One locale resolved against the corpus: every field the library's culture record needs, taken
// from the first locale in the inheritance chain that declares it.
final private[sbt] case class Resolved
  (
    tag: String,
    chain: Vector[String],
    fields: Map[String, String],
    digits: String,
    active: String,
    direction: String,
    calendar: String,
    decimal: Format,
    percent: Format,
    monetary: Format,
    accounting: Format,
    monetaryAlpha: Format,
    accountingAlpha: Format,
    numberings: Vector[(String, Numbering)],
    symbols: Vector[(String, String)],
    currencies: Vector[(String, Vector[(String, String)])],
    currencyNames: Vector[(String, String)],
    territories: Vector[(String, String)],
    languages: Vector[(String, String)],
    scripts: Vector[(String, String)],
    cardinal: String,
    ordinal: String
  )

// Resolves declared locales against the curated corpus, and writes them as the source a build
// compiles.
//
// Resolution is CLDR's own: a value comes from the first locale in the inheritance chain that
// declares it, and the chain follows the curated parent-locale rows before truncation. Nothing is
// synthesised - a locale that declares no accounting form resolves to its standard one because
// that is what CLDR's alias says, not because the generator invents parentheses.
private[sbt] object Cultures:

  private def scalar(corpus: Corpus, chain: Vector[String], rows: Map[String, Vector[String]], column: String): Either[Fault, String] =
    val declared = chain.iterator
      .flatMap(tag => rows.get(tag))
      .map(row => corpus.cultures.at(row, column))
      .find {
        case Right(value) => value.nonEmpty
        case Left(_)      => true
      }
    declared.getOrElse(Right(""))

  private def list(value: String): Vector[String] = if value.isEmpty then Vector.empty else value.split("\\|", -1).toVector

  // The locale's script, from the tag where it names one and from the likely-subtags data
  // otherwise, which is what the writing direction is a property of.
  private def script(corpus: Corpus, tag: String): String =
    val subtags = tag.split("-").toVector
    subtags.find(part => part.length == 4 && part.headOption.exists(_.isUpper)) match
      case Some(explicit) => explicit
      case None           => corpus.likely.get(subtags.headOption.getOrElse(tag)).map(_._1).getOrElse("Latn")

  private def region(corpus: Corpus, tag: String): String =
    val subtags = tag.split("-").toVector
    subtags.drop(1).find(part => part.length == 2 && part.forall(_.isUpper)) match
      case Some(explicit) => explicit
      case None           => corpus.likely.get(subtags.headOption.getOrElse(tag)).map(_._2).getOrElse("")

  // World ships these labellings; a territory whose CLDR preference names another takes the first
  // one on its own ordered list that world carries, which is what that list is for.
  private val calendars = Map
    (
      "gregorian" -> "Gregorian",
      "buddhist" -> "Buddhist",
      "roc" -> "ROC",
      "coptic" -> "Coptic",
      "ethiopic" -> "Ethiopic"
    )

  private def calendar(corpus: Corpus, tag: String): Either[Fault, String] =
    val territory = region(corpus, tag)
    val ordered = corpus.calendars.rows
      .find(row => corpus.calendars.at(row, "territory").fold(_ => false, _ == territory))
      .orElse(corpus.calendars.rows.find(row => corpus.calendars.at(row, "territory").fold(_ => false, _ == "001")))
    ordered match
      case None      => Right("Gregorian")
      case Some(row) =>
        corpus.calendars.at(row, "calendars").map { preference =>
          list(preference).flatMap(calendars.get).headOption.getOrElse("Gregorian")
        }

  private def rules(corpus: Corpus, tag: String, kind: String): Either[Fault, Vector[(String, String)]] =
    val language = tag.split("-").headOption.getOrElse(tag)
    val read = corpus.plurals.rows
      .filter
        (row =>
          corpus.plurals.at(row, "language").fold(_ => false, _ == language)
            && corpus.plurals.at(row, "kind").fold(_ => false, _ == kind))
      .map
        (row =>
          for
            category <- corpus.plurals.at(row, "category")
            rule <- corpus.plurals.at(row, "rule")
          yield category -> rule)
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(pair) => pair })
  end rules

  // A generic currency name is the row that carries neither a symbol nor a plural category: the
  // count-less picker label CLDR states alongside the counted forms.
  private def genericRow(corpus: Corpus, row: Vector[String]): Boolean =
    corpus.currencies.at(row, "symbol").fold(_ => false, _.isEmpty)
      && corpus.currencies.at(row, "count").fold(_ => false, _.isEmpty)

  // Every numbering system the locale declares, each carrying its own separators.
  private def systemsOf(corpus: Corpus, chain: Vector[String]): Either[Fault, Vector[(String, Numbering)]] =
    val columns = Vector("decimal", "group", "minimum", "minusSign", "plusSign", "percentSign", "perMille")
    val gathered = chain.reverse.foldLeft(Vector.empty[(String, Vector[String])]) { (acc, tag) =>
      val rows = corpus.systems.rows.filter(row => corpus.systems.at(row, "locale").fold(_ => false, _ == tag))
      rows.foldLeft(acc) { (inner, row) =>
        (for
          system <- corpus.systems.at(row, "system")
          values <- cells(corpus.systems, row, columns)
        yield
          // A nearer declaration replaces only what it declares, so a system stating its signs alone
          // keeps the separators it inherited.
          val previous = inner.find(_._1 == system).map(_._2).getOrElse(columns.map(_ => ""))
          val merged = values.zip(previous).map((next, was) => if next.isEmpty then was else next)
          inner.filterNot(_._1 == system) :+ (system -> merged)
        ).getOrElse(inner)
      }
    }
    val read = gathered.sortBy(_._1).map { (system, values) =>
      digitsOf(corpus, system).map { digits =>
        system -> Numbering
          (
            digits,
            values(0),
            values(1),
            values(2).toIntOption.getOrElse(1),
            values(3),
            values(4),
            values(5),
            values(6)
          )
      }
    }
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(pair) => pair })
  end systemsOf

  private def cells(table: Table, row: Vector[String], columns: Vector[String]): Either[Fault, Vector[String]] =
    val read = columns.map(column => table.at(row, column))
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(cell) => cell })

  private def sliced(table: Table, chain: Vector[String], key: String, value: String): Either[Fault, Vector[(String, String)]] =
    sliced(table, chain, key, value, _ => true)

  private def sliced(table: Table, chain: Vector[String], key: String, value: String, admits: Vector[String] => Boolean): Either[
    Fault,
    Vector[(String, String)]] =
    // The nearest declaration in the chain wins, exactly as a scalar field does, so a regional
    // locale that renames one territory keeps its parent's other names.
    val gathered = chain.reverse.foldLeft(Vector.empty[(String, String)]) { (acc, tag) =>
      val rows = table.rows.filter(row => table.at(row, "locale").fold(_ => false, _ == tag) && admits(row))
      rows.foldLeft(acc) { (inner, row) =>
        (for
          code <- table.at(row, key)
          text <- table.at(row, value)
        yield
          if text.isEmpty then inner
          else inner.filterNot(_._1 == code) :+ (code -> text)).getOrElse(inner)
      }
    }
    Right(gathered.sortBy(_._1))
  end sliced

  // The numbering system a `u-nu` extension selects, and the tag with the extension removed: the
  // culture is built against the locale, and the extension chooses which declared system is active.
  private def selected(declared: String): (String, Option[String]) =
    val marker = "-u-nu-"
    declared.indexOf(marker) match
      case -1 => (declared, None)
      case at =>
        val system = declared.substring(at + marker.length).takeWhile(_ != '-')
        (declared.substring(0, at), Some(system))

  // World's own registers decide what a generated culture may name: CLDR knows historic currencies
  // and numeric region codes that world's value types do not carry, and naming one would emit a
  // member that does not exist.
  private def register(table: Table, column: String): Set[String] =
    table.rows.flatMap(row => table.at(row, column).toOption).filter(_.nonEmpty).toSet

  // Resolves one declared locale, refusing what no dataset can source.
  def resolve(corpus: Corpus, declared: String): Either[Fault, Resolved] =
    val (base, requested) = selected(declared.replace('_', '-'))
    val tag = base
    for
      _ <- Either.cond
             (
               !tag.startsWith("x-") && !tag.contains("-x-"),
               (),
               Fault.PrivateUse(declared)
             )
      _ <- Either.cond
             (
               tag.split("-").forall(part => part.nonEmpty && part.forall(_.isLetterOrDigit)),
               (),
               Fault.Tag(declared, "not a well-formed language tag")
             )
      rows <- corpus.cultures.keyed("locale")
      chain = corpus.chain(tag)
      // Root sources every locale by construction, so it cannot be the evidence that a declared one
      // is real: the language must be a language CLDR knows, and a named region a territory world
      // carries - the alternative is emitting a member name that does not exist.
      _ <- Either.cond
             (
               corpus.likely.contains(tag.split("-").headOption.getOrElse(tag))
                 || chain.filterNot(_ == "root").exists(rows.contains),
               (),
               Fault.Unsourced(declared)
             )
      _ <- Either.cond
             (
               region(corpus, tag).isEmpty || !tag.contains("-")
                 || corpus.territories.rows.exists(row => corpus.territories.at(row, "alpha2").fold(_ => false, _ == region(corpus, tag))),
               (),
               Fault.Unsourced(declared)
             )
      fields <- fieldsOf(corpus, chain, rows)
      declaredSystems <- systemsOf(corpus, chain)
      // A `u-nu` extension may only select a system the locale DECLARES: an undeclared one is absent
      // data, and guessing separators for it is the synthesis the whole architecture refuses.
      active <- requested match
                  case None         => Right(fields.getOrElse("numbering", "latn"))
                  case Some(system) =>
                    Either.cond
                      (
                        declaredSystems.exists(_._1 == system),
                        system,
                        Fault.Tag(declared, s"a request for the numbering system '$system', which it does not declare")
                      )
      digits <- digitsOf(corpus, active)
      calendarName <- calendar(corpus, tag)
      symbolText = Symbols
                     (
                       minus = fields.getOrElse("minusSign", "-"),
                       plus = fields.getOrElse("plusSign", "+"),
                       percent = fields.getOrElse("percentSign", "%"),
                       perMille = fields.getOrElse("perMille", "\u2030")
                     )
      decimal <- Patterns.compile(fields.getOrElse("decimalPattern", "#,##0.###"), symbolText)
      percent <- Patterns.compile(fields.getOrElse("percentPattern", "#,##0%"), symbolText)
      standardPattern = fields.getOrElse("currencyStandard", "\u00A4\u00A0#,##0.00")
      monetary <- Patterns.compile(standardPattern, symbolText)
      // CLDR aliases a locale's accounting form to its standard one where it declares none, so the
      // bookkeeping parentheses appear only where the locale's own data carries them.
      accounting <- Patterns.compile(fields.get("currencyAccounting").filter(_.nonEmpty).getOrElse(standardPattern), symbolText)
      monetaryAlpha <- Patterns.compile(fields.get("currencyStandardAlpha").filter(_.nonEmpty).getOrElse(standardPattern), symbolText)
      accountingAlpha <- Patterns.compile
                           (
                             fields
                               .get("currencyAccountingAlpha")
                               .filter(_.nonEmpty)
                               .orElse(fields.get("currencyAccounting").filter(_.nonEmpty))
                               .getOrElse(fields.get("currencyStandardAlpha").filter(_.nonEmpty).getOrElse(standardPattern)),
                             symbolText
                           )
      currencyCodes = register(corpus.currencyRegister, "code")
      territoryCodes = register(corpus.territories, "alpha2")
      languageCodes = register(corpus.languageRegister, "subtag")
      scriptCodes = register(corpus.scriptRegister, "code")
      symbols <- currencySymbols(corpus, chain)
      names <- currencyNames(corpus, chain)
      generic <- sliced(corpus.currencies, chain, "currency", "name", row => genericRow(corpus, row))
      cardinalRules <- rules(corpus, tag, "cardinal")
      ordinalRules <- rules(corpus, tag, "ordinal")
      cardinal <- Plurals.cardinal(cardinalRules)
      ordinal <- Plurals.ordinal(ordinalRules)
      display <- displayNames(corpus, chain)
    yield Resolved
      (
        tag = tag,
        chain = chain,
        fields = fields,
        digits = digits,
        active = active,
        direction = if corpus.directions.get(script(corpus, tag)).contains("rtl") then "RightToLeft" else "LeftToRight",
        calendar = calendarName,
        decimal = decimal,
        percent = percent,
        monetary = monetary,
        accounting = accounting,
        monetaryAlpha = monetaryAlpha,
        accountingAlpha = accountingAlpha,
        numberings = declaredSystems,
        symbols = symbols.filter((code, _) => currencyCodes.contains(code)),
        currencies = names.filter((code, _) => currencyCodes.contains(code)),
        currencyNames = generic.filter((code, _) => currencyCodes.contains(code)),
        territories = display.getOrElse("territory", Vector.empty).filter((code, _) => territoryCodes.contains(code)),
        languages = display.getOrElse("language", Vector.empty).filter((code, _) => languageCodes.contains(code) && code.length == 2),
        scripts = display.getOrElse("script", Vector.empty).filter((code, _) => scriptCodes.contains(code)),
        cardinal = cardinal,
        ordinal = ordinal
      )
    end for
  end resolve

  private def fieldsOf(corpus: Corpus, chain: Vector[String], rows: Map[String, Vector[String]]): Either[Fault, Map[String, String]] =
    val read = corpus.cultures.columns.filterNot(_ == "locale").map(column => scalar(corpus, chain, rows, column).map(column -> _))
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(pair) => pair }.toMap)

  private def digitsOf(corpus: Corpus, system: String): Either[Fault, String] =
    corpus.numbering.rows.find(row => corpus.numbering.at(row, "system").fold(_ => false, _ == system)) match
      case None      => Left(Fault.Corpus(s"carries no digits for the numbering system '$system'"))
      case Some(row) => corpus.numbering.at(row, "digits")

  private def currencySymbols(corpus: Corpus, chain: Vector[String]): Either[Fault, Vector[(String, String)]] =
    sliced(corpus.currencies, chain, "currency", "symbol")

  private def currencyNames(corpus: Corpus, chain: Vector[String]): Either[Fault, Vector[(String, Vector[(String, String)])]] =
    val gathered = chain.reverse.foldLeft(Vector.empty[(String, String, String)]) { (acc, tag) =>
      val rows = corpus.currencies.rows.filter(row => corpus.currencies.at(row, "locale").fold(_ => false, _ == tag))
      rows.foldLeft(acc) { (inner, row) =>
        (for
          currency <- corpus.currencies.at(row, "currency")
          count <- corpus.currencies.at(row, "count")
          name <- corpus.currencies.at(row, "name")
        yield
          if count.isEmpty || name.isEmpty then inner
          else inner.filterNot((c, k, _) => c == currency && k == count) :+ (currency, count, name)).getOrElse(inner)
      }
    }
    Right
      (
        gathered
          .groupBy(_._1)
          .toVector
          .sortBy(_._1)
          .map((currency, entries) => currency -> entries.map((_, count, name) => count -> name).sortBy(_._1))
      )
  end currencyNames

  private def displayNames(corpus: Corpus, chain: Vector[String]): Either[Fault, Map[String, Vector[(String, String)]]] =
    val gathered = chain.reverse.foldLeft(Vector.empty[(String, String, String)]) { (acc, tag) =>
      val rows = corpus.names.rows.filter(row => corpus.names.at(row, "locale").fold(_ => false, _ == tag))
      rows.foldLeft(acc) { (inner, row) =>
        (for
          kind <- corpus.names.at(row, "kind")
          code <- corpus.names.at(row, "code")
          name <- corpus.names.at(row, "name")
        yield
          if name.isEmpty then inner
          else inner.filterNot((k, c, _) => k == kind && c == code) :+ (kind, code, name)).getOrElse(inner)
      }
    }
    Right(gathered.groupBy(_._1).map((kind, entries) => kind -> entries.map((_, code, name) => code -> name).sortBy(_._1)))
  end displayNames
end Cultures
