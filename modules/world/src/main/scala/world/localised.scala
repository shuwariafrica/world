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

import boilerplate.nullable.*

/** The application's own content in several locales, resolved down world's
  * fallback chain: the chain is world's to get right, the content stays the
  * application's. Instances via [[Localised$ Localised]].
  */
final case class Localised[A] private (values: Map[Locale, A])

/** Construction and resolution for [[Localised]]. */
object Localised:
  def apply[A](entries: (Locale, A)*): Localised[A] = new Localised(entries.toMap)

  /** The holding with `locale` bound to `value`. */
  def updated[A](l: Localised[A], locale: Locale, value: A): Localised[A] = l.updated(locale, value)

  /** Resolves by truncation: the exact tag, then progressively shorter prefixes
    * of it.
    */
  def resolve[A](l: Localised[A], locale: Locale): Option[A] = l.resolve(locale)

  /** Total resolution against an application default. */
  @targetName("resolveOrDefault")
  def resolve[A](l: Localised[A], locale: Locale, default: A): A = l.resolve(locale, default)

  extension [A](l: Localised[A])
    @targetName("ext_updated")
    def updated(locale: Locale, value: A): Localised[A] =
      new Localised(l.values.updated(locale, value))

    // CLDR parent overrides join at the data pipeline; the chain itself is pure truncation.
    @targetName("ext_resolve")
    def resolve(locale: Locale): Option[A] =
      @tailrec def walk(tag: String): Option[A] =
        if tag.isEmpty then None
        else
          l.values.find((k, _) => k.value == tag) match
            case Some((_, a)) => Some(a)
            case None         =>
              walk
                (tag.lastIndexOf('-') match
                  case -1 => ""
                  case i  => tag.substring(0, i).unsafe)
      walk(locale.value)
    end resolve

    @targetName("ext_resolveOrDefault")
    def resolve(locale: Locale, default: A): A = l.resolve(locale).getOrElse(default)
  end extension

  given [A] => CanEqual[Localised[A], Localised[A]] = CanEqual.derived
end Localised
