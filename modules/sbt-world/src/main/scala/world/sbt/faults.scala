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

import scala.util.control.NoStackTrace

// Everything generation refuses to do, as values: the compilation stages hand faults back rather
// than failing where they are found, so a build sees one directed message naming the declaration
// that caused it.
sealed abstract private[sbt] class Fault(val message: String) extends Exception(message), NoStackTrace derives CanEqual

private[sbt] object Fault:

  // A pattern outside the subset world models, quoting the pattern as the corpus stores it.
  final case class Pattern(pattern: String, detail: String) extends Fault(s"the pattern '$pattern' carries $detail")

  // A locale declaration that is not a well-formed language tag.
  final case class Tag(declared: String, detail: String) extends Fault(s"the declared locale '$declared' is $detail")

  // A private-use declaration: no dataset can source one, so the generator refuses rather than
  // emitting a bundle that pretends to carry locale data.
  final case class PrivateUse(declared: String)
      extends Fault
        (
          s"the declared locale '$declared' is private use, which no dataset can source - compose that bundle by hand " +
            "through the Culture(locale, data) constructor"
        )

  // A locale the curated corpus does not carry.
  final case class Unsourced(declared: String)
      extends Fault(s"the declared locale '$declared' is not in world's curated presentation corpus")

  // The corpus itself is unreadable, absent, or inconsistent with the generator's expectations.
  final case class Corpus(detail: String) extends Fault(s"world's curated presentation corpus $detail")

  // A plural rule outside the subset the rule compiler models.
  final case class Rule(rule: String, detail: String) extends Fault(s"the plural rule '$rule' carries $detail")

  // A message catalogue entry the generator cannot turn into a typed method.
  final case class Catalogue(key: String, detail: String) extends Fault(s"the message '$key' $detail")

  // A translation that does not answer its catalogue entry.
  final case class Translation(locale: String, key: String, detail: String) extends Fault(s"the $locale translation of '$key' $detail")
end Fault
