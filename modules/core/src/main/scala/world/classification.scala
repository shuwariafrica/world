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

/** The personal-data class a value's own content carries, under the convergent
  * statutory definitions: `None` for values that are not about a person,
  * `Personal` for information of an identified or identifiable natural person,
  * `Special` for the further-protected categories (health, biometrics, beliefs,
  * and kin). World ships no `Special` type; the case exists for consumer types.
  *
  * The instances carry the natural-person reading. POPIA s.1 additionally
  * reaches identifiable juristic persons, so a consumer under it raises an
  * organisation-bearing type through a local [[Classified]] instance.
  */
enum Classification derives CanEqual:
  case None, Personal, Special

/** Severity ordering for [[Classification]], so a mixed record folds to its
  * dominant class.
  */
object Classification:
  given Ordering[Classification] = Ordering.by(_.ordinal)

/** A type's personal-data classification, read by redaction, retention, and
  * grounding gates. World supplies instances for its own types in their
  * companions; a consumer's types join through [[Classified$ Classified]]. A
  * classification change is a semantic-version event, since retention decisions
  * rest on it.
  */
trait Classified[A]:
  def classification: Classification

  /** Per-field classes where the type mixes them; empty for uniform types. */
  def fields: Vector[(String, Classification)]

/** Uniform and mixed instance construction for [[Classified]]. */
object Classified:
  /** A uniform instance: every field the one class. */
  def of[A](c: Classification): Classified[A] = new Classified[A]:
    def classification: Classification = c
    def fields: Vector[(String, Classification)] = Vector.empty

  /** A mixed instance from its field classes; the dominant class is their
    * maximum.
    */
  def of[A](entries: (String, Classification)*): Classified[A] = new Classified[A]:
    def classification: Classification =
      entries.map(_._2).maxOption(using Ordering[Classification]).getOrElse(Classification.None)
    def fields: Vector[(String, Classification)] = entries.toVector
end Classified
