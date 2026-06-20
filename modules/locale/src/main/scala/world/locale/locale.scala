/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package world.locale

import scala.annotation.targetName

import world.locale.country.Alpha2Code
import world.locale.country.Countries
import world.locale.country.Country
import world.locale.language.Language
import world.locale.language.LanguageCode
import world.locale.language.Languages
import world.locale.script.Script
import world.locale.script.ScriptCode
import world.locale.script.Scripts

/** A BCP 47 locale identifier: language with optional script, region, and variants.
  *
  * Instances are constructed via [[Locale$ Locale]].
  *
  * @param language The language subtag (ISO 639).
  * @param script The optional script subtag (ISO 15924).
  * @param region The optional region subtag (ISO 3166-1 alpha-2).
  * @param variants Zero or more variant subtags.
  *
  * @example
  *   {{{
  * import world.locale.*
  * import world.locale.language.LanguageCode
  * import world.locale.country.Alpha2Code
  *
  * Locale(LanguageCode("sw"), Alpha2Code("KE")).toBcp47  // "sw-KE"
  * Locale.from("zh-Hant-TW")                             // Right(Locale(...))
  *   }}}
  */
final case class Locale
  (
    language: LanguageCode,
    script: Option[ScriptCode],
    region: Option[Alpha2Code],
    variants: Vector[String]
  ) derives CanEqual

/** Factory methods and extensions for [[Locale]]. */
object Locale:

  /** The CLDR root locale (undetermined language). */
  val Root: Locale = Locale(LanguageCode("und"), None, None, Vector.empty)

  /** Creates a [[Locale]] with only a language. */
  def apply(language: LanguageCode): Locale =
    Locale(language, None, None, Vector.empty)

  /** Creates a [[Locale]] with a language and region. */
  @targetName("applyRegion")
  def apply(language: LanguageCode, region: Alpha2Code): Locale =
    Locale(language, None, Some(region), Vector.empty)

  /** Creates a [[Locale]] with a language, script, and region. */
  @targetName("applyScriptRegion")
  def apply(language: LanguageCode, script: ScriptCode, region: Alpha2Code): Locale =
    Locale(language, Some(script), Some(region), Vector.empty)

  /** Parses a BCP 47 language tag into a [[Locale]].
    *
    * Accepts language, optional script, optional region, and variant subtags;
    * the underscore separator (Java convention) is also accepted. Extension and
    * private-use sequences (`-u-`, `-t-`, `-x-`) are not modelled and are dropped.
    * A malformed subtag (for example a script following a region) is rejected.
    *
    * @return `Right` with the parsed [[Locale]], or `Left` with a [[errors.LocaleError]].
    */
  def from(tag: String): Either[errors.LocaleError, Locale] =
    val trimmed = tag.trim
    if trimmed.isEmpty then Left(errors.InvalidLocaleTag(tag))
    else
      trimmed.split("[-_]").toList match
        case Nil             => Left(errors.InvalidLocaleTag(tag))
        case langStr :: rest =>
          LanguageCode.from(langStr) match
            case Left(_)     => Left(errors.InvalidLocaleTag(langStr))
            case Right(lang) => parseAfterLanguage(lang, rest)

  /** Parses a known-valid BCP 47 tag, throwing on failure. Use [[from]] for untrusted input. */
  def fromUnsafe(tag: String): Locale =
    from(tag) match
      case Right(locale) => locale
      case Left(error)   => throw error // scalafix:ok

  extension (locale: Locale)

    /** Serialises this locale to a BCP 47 language tag, e.g. `"en-Latn-GB"`. */
    def toBcp47: String =
      val sb = new StringBuilder
      sb.append(LanguageCode.unwrap(locale.language))
      locale.script.foreach(s => sb.append('-').append(ScriptCode.unwrap(s)))
      locale.region.foreach(r => sb.append('-').append(Alpha2Code.unwrap(r)))
      locale.variants.foreach(v => sb.append('-').append(v))
      sb.toString

    /** Fills in the most likely script and region from CLDR likely-subtags data. */
    def maximise: Locale =
      val lang = LanguageCode.unwrap(locale.language)
      val script = locale.script.map(ScriptCode.unwrap)
      val region = locale.region.map(Alpha2Code.unwrap)
      val candidates = List
        (
          Some(List(Some(lang), script, region).flatten.mkString("-")),
          region.map(r => s"$lang-$r"),
          script.map(s => s"$lang-$s"),
          Some(lang)
        ).flatten
      candidates.iterator.flatMap(LikelySubtags.resolve).nextOption().flatMap(from(_).toOption) match
        case Some(max) =>
          Locale(max.language, locale.script.orElse(max.script), locale.region.orElse(max.region), locale.variants)
        case None => locale
    end maximise

    /** Removes the script and region subtags that CLDR would infer, yielding the shortest
      * equivalent tag.
      */
    def minimise: Locale =
      val max = locale.maximise
      val trials = List
        (
          Some(Locale(max.language, None, None, max.variants)),
          max.region.map(r => Locale(max.language, None, Some(r), max.variants)),
          max.script.map(s => Locale(max.language, Some(s), None, max.variants))
        ).flatten
      trials.find(_.maximise == max).getOrElse(max)

    /** Resolves the language subtag to its [[Language]] singleton, if known. */
    def resolvedLanguage: Option[Language] = Languages.from(locale.language)

    /** Resolves the script subtag to its [[Script]] singleton, if present and known. */
    def resolvedScript: Option[Script] = locale.script.flatMap(Scripts.from)

    /** Resolves the region subtag to its [[Country]] singleton, if present and known. */
    def resolvedRegion: Option[Country] = locale.region.flatMap(Countries.from)

  end extension

  // --- Private parsing ---

  private def parseAfterLanguage(lang: LanguageCode, parts: List[String]): Either[errors.LocaleError, Locale] =
    parts match
      case Nil                            => Right(Locale(lang, None, None, Vector.empty))
      case head :: rest if isScript(head) =>
        ScriptCode
          .from(head)
          .left
          .map(_ => errors.InvalidLocaleTag(head))
          .flatMap(script => parseAfterScript(lang, Some(script), rest))
      case head :: rest if isRegion(head) =>
        Alpha2Code
          .from(head)
          .left
          .map(_ => errors.InvalidLocaleTag(head))
          .flatMap(region => variantsOf(rest).map(vs => Locale(lang, None, Some(region), vs)))
      case other => variantsOf(other).map(vs => Locale(lang, None, None, vs))

  private def parseAfterScript(lang: LanguageCode, script: Option[ScriptCode], parts: List[String]): Either[errors.LocaleError, Locale] =
    parts match
      case Nil                            => Right(Locale(lang, script, None, Vector.empty))
      case head :: rest if isRegion(head) =>
        Alpha2Code
          .from(head)
          .left
          .map(_ => errors.InvalidLocaleTag(head))
          .flatMap(region => variantsOf(rest).map(vs => Locale(lang, script, Some(region), vs)))
      case other => variantsOf(other).map(vs => Locale(lang, script, None, vs))

  /** Collects leading variant subtags, dropping a trailing extension/private-use sequence
    * (which begins with a singleton subtag). Any non-variant before such a sequence is malformed.
    */
  private def variantsOf(parts: List[String]): Either[errors.LocaleError, Vector[String]] =
    val variants = parts.takeWhile(_.length != 1)
    variants.find(p => !isVariant(p)) match
      case Some(bad) => Left(errors.InvalidLocaleTag(bad))
      case None      => Right(variants.toVector)

  /** Script subtag: exactly 4 ASCII letters. */
  private def isScript(s: String): Boolean =
    s.length == 4 && s.forall(c => (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))

  /** Region subtag: 2 ASCII letters or 3 ASCII digits. */
  private def isRegion(s: String): Boolean =
    (s.length == 2 && s.forall(c => (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) ||
      (s.length == 3 && s.forall(c => c >= '0' && c <= '9'))

  /** Variant subtag: 5-8 alphanumerics, or a digit followed by 3 alphanumerics. */
  private def isVariant(s: String): Boolean =
    (s.length >= 5 && s.length <= 8 && s.forall(_.isLetterOrDigit)) ||
      (s.length == 4 && s.head.isDigit && s.tail.forall(_.isLetterOrDigit))

end Locale
