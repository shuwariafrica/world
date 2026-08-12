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
package world

import scala.annotation.tailrec
import scala.annotation.targetName
import scala.util.boundary
import scala.util.boundary.break

import boilerplate.ValueCodec
import boilerplate.codec.ASCII
import boilerplate.nullable.*

/** A BCP 47 locale identifier held as its canonical tag, so equality, ordering,
  * and storage all behave as the tag does. Instances via [[Locale$ Locale]].
  */
opaque type Locale = String

/** Factory, parsing, composition, and negotiation for [[Locale]]. */
object Locale:

  /** Why a tag was refused, each case carrying the offending subtag. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Syntax(tag: String) extends Invalid("malformed language tag")
    final case class Language(subtag: String) extends Invalid("unknown language")
    final case class Script(subtag: String) extends Invalid("unknown script")
    final case class Region(subtag: String) extends Invalid("unknown region")
    final case class Variant(subtag: String) extends Invalid("invalid variant")

  private object likely:
    def script(l: world.Language): Option[world.Script] =
      packed.optional(tables.likelyScript, l.index).map(world.Script.fromIndex)
    def region(l: world.Language): Option[world.Region] =
      packed.optional(tables.likelyRegion, l.index).map(world.Region.fromIndex)

  def apply(language: Language): Locale = language.code
  def apply(language: Language, region: Region): Locale = s"${language.code}-${region.subtag}"
  // Opaque components erase to primitives, so same-arity overloads need distinct target names.
  @targetName("applyScript")
  def apply(language: Language, script: Script): Locale = s"${language.code}-${script.code}"
  def apply(language: Language, script: Script, region: Region): Locale =
    s"${language.code}-${script.code}-${region.subtag}"

  /** Parses and canonicalises a BCP 47 tag, private-use tags (`x-...`)
    * included, so a genuinely unregistered context is nameable without
    * pretending registration.
    *
    * Extension and private-use subtags are preserved but not interpreted. The
    * registry's grandfathered irregular tags are refused.
    */
  def parse(tag: String): Either[Invalid, Locale] =
    tag.split('-').toList match
      case x :: rest
          if (x == "x" || x == "X") && rest.nonEmpty
            && rest.forall(p => p.length <= 8 && ASCII.isAlphanumeric(p)) =>
        Right(("x" :: rest.map(p => ASCII.lower(p))).mkString("-"))
      case first :: rest if first.length >= 2 && first.length <= 3 && ASCII.isLetters(first) =>
        world.Language.from(first) match
          case Left(_)     => Left(Invalid.Language(first))
          case Right(lang) => components(lang, rest)
      case _ => Left(Invalid.Syntax(tag))

  private def components(lang: Language, rest: List[String]): Either[Invalid, Locale] =
    boundary:
      def script(remaining: List[String]): (Option[String], List[String]) = remaining match
        case s :: t if s.length == 4 && ASCII.isLetters(s) =>
          world.Script.from(s) match
            case Right(sc) => (Some(sc.code), t)
            case Left(_)   => break(Left(Invalid.Script(s)))
        case _ => (None, remaining)

      def region(remaining: List[String]): (Option[String], List[String]) = remaining match
        case r :: t if r.length == 2 && ASCII.isLetters(r) =>
          Territory.from(r) match
            case Right(terr) => (Some(terr.alpha2), t)
            case Left(_)     => break(Left(Invalid.Region(r)))
        case r :: t if r.length == 3 && ASCII.isDigits(r) =>
          // A territory's own numeric is not a region subtag: only macro areas take the
          // three-digit form.
          world.Region.from(r.toInt).toOption.filter(_.territory.isEmpty) match
            case Some(area) => (Some(area.subtag), t)
            case None       => break(Left(Invalid.Region(r)))
        case _ => (None, remaining)

      @tailrec def variants(remaining: List[String], acc: List[String]): (List[String], List[String]) =
        remaining match
          case v :: t
              if (v.length >= 5 && v.length <= 8 && ASCII.isAlphanumeric(v))
                || (v.length == 4 && ASCII.isDigit(v.head) && ASCII.isAlphanumeric(v)) =>
            variants(t, ASCII.lower(v) :: acc)
          case _ => (acc.reverse, remaining)

      val (sc, afterScript) = script(rest)
      val (re, afterRegion) = region(afterScript)
      val (vars, tail) = variants(afterRegion, Nil)
      val canonical = lang.code :: (sc.toList ++ re.toList ++ vars)
      tail match
        case Nil                         => Right(canonical.mkString("-"))
        case ext :: _ if ext.length == 1 =>
          Right((canonical ++ tail.map(p => ASCII.lower(p))).mkString("-"))
        case bad :: _ => Left(Invalid.Variant(bad))

  /** Resolves an `Accept-Language` header against a supported set by the RFC
    * 4647 Lookup scheme: ranges are parsed, zero-quality entries dropped, and
    * the rest tried in weight order. `None` when nothing matches. A caller that
    * has already parsed the header feeds the `Seq` overload.
    */
  def negotiate(preferences: String, supported: Seq[Locale]): Option[Locale] =
    val ranges = preferences
      .split(',')
      .toVector
      .map(_.trim.unsafe)
      .filter(_.nonEmpty)
      .map { part =>
        part.split(';').toList match
          case range :: params =>
            val q = params
              .map(_.trim.unsafe)
              .collectFirst { case p if p.startsWith("q=") => p.drop(2).toDoubleOption }
              .flatten
              .getOrElse(1.0)
            (range.trim.unsafe, q)
          case Nil => (part, 1.0)
      }
      .filter((_, q) => q > 0)
      .sortBy((_, q) => -q)
    negotiate(ranges.map((r, _) => r), supported)
  end negotiate

  /** The same Lookup over ranges the caller has already ordered by preference,
    * for a server that parses the wire grammar itself. Order carries the
    * weighting, and dropping zero-quality ranges is the caller's.
    */
  @targetName("negotiateOrdered")
  def negotiate(preferences: Seq[String], supported: Seq[Locale]): Option[Locale] =
    def lookup(range: String): Option[Locale] =
      @tailrec def truncate(candidate: String): Option[Locale] =
        if candidate.isEmpty then None
        else
          supported.find(s => s.equalsIgnoreCase(candidate)) match
            case Some(hit) => Some(hit)
            case None      =>
              val cut = candidate.lastIndexOf('-')
              truncate(if cut > 0 then candidate.substring(0, cut).unsafe else "")
      if range == "*" then supported.headOption else truncate(range)
    preferences.iterator.map(lookup).collectFirst { case Some(l) => l }
  end negotiate

  extension (l: Locale)
    /** The canonical BCP 47 tag. */
    def value: String = l

    /** The primary language, where the tag carries one. A private-use tag does
      * not, and nothing is substituted for it.
      */
    def language: Option[Language] = world.Language.from(l.takeWhile(_ != '-')).toOption
    // A one-character subtag opens the extension and private-use sections, whose payloads are
    // uninterpreted: the `Latn` in `en-t-sc-latn` is a transform subtag, not this tag's script.
    def script: Option[Script] =
      l.split('-')
        .toList
        .drop(1)
        .takeWhile(_.length != 1)
        .collectFirst {
          case s if s.length == 4 && ASCII.isLetters(s) => world.Script.from(s).toOption
        }
        .flatten
    def region: Option[Region] =
      l.split('-')
        .toList
        .drop(1)
        .takeWhile(_.length != 1)
        .collectFirst {
          case r if r.length == 2 && ASCII.isLetters(r) => Territory.from(r).toOption
          case r if r.length == 3 && ASCII.isDigits(r)  => world.Region.from(r.toInt).toOption
        }
        .flatten
    def variants: Vector[String] =
      l.split('-')
        .toVector
        .drop(1)
        .takeWhile(_.length != 1)
        .filter(v => (v.length >= 5 && v.length <= 8) || (v.length == 4 && ASCII.isDigit(v.head)))

    /** Adds the most likely script and region where absent, per UTS 35.
      * Identity where the data holds no answer.
      */
    def maximise: Locale =
      (l.script, l.region) match
        case (Some(_), Some(_)) => l
        case (sc, re)           =>
          l.language match
            case None       => l
            case Some(lang) =>
              (likely.script(lang), likely.region(lang)) match
                case (Some(ls), Some(lr)) => Locale(lang, sc.getOrElse(ls), re.getOrElse(lr))
                case _                    => l

    /** The shortest tag that maximises to the same thing, per UTS 35. Identity
      * for a tag with no language to shorten towards.
      */
    def minimise: Locale =
      l.language match
        case None       => l
        case Some(lang) =>
          val max = l.maximise
          if Locale(lang).maximise == max then Locale(lang)
          else
            val byRegion = max.region.map(r => Locale(lang, r))
            val byScript = max.script.map(s => Locale(lang, s))
            byRegion
              .filter(_.maximise == max)
              .orElse(byScript.filter(_.maximise == max))
              .getOrElse(max)
  end extension

  given CanEqual[Locale, Locale] = CanEqual.derived
  given Ordering[Locale] = Ordering.String.on(identity)
  given ValueCodec.Aux[Locale, Invalid] = ValueCodec(parse, l => Locale.value(l))
end Locale
