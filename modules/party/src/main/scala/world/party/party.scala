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
package world.party

import world.*
import world.address.Address
import world.id.Email
import world.id.Phone

/** A person's name in CLDR's fields, under their British names. Capture it as
  * separate fields, as one unstructured `full` string, or as both; `usable`
  * says whether enough of it is there to address someone by. The name's own
  * locale travels with it, so it can be presented by its owner's conventions
  * rather than the reader's. There is no parser from free text into these
  * fields, because CLDR declines to define one. Instances via [[Name$ Name]].
  */
final case class Name private (
  full: Option[String],
  title: Option[String],
  forename: Option[String],
  forename2: Option[String],
  surname: Option[String],
  surname2: Option[String],
  prefix: Option[String],
  generation: Option[String],
  credentials: Option[String],
  locale: Option[Locale]
) derives CanEqual

/** Construction for [[Name]]: whole-name entry points, then per-field
  * builders.
  */
object Name:
  /** The name as one free-text box gave it. */
  def apply(full: String): Name = Name(Some(full), None, None, None, None, None, None, None, None, None)

  /** The two fields most capture forms ask for. */
  def apply(forename: String, surname: String): Name =
    Name(None, None, Some(forename), None, Some(surname), None, None, None, None, None)

  /** A name that is one word, held as the surname - either core field alone is
    * enough to address someone by.
    */
  def mononym(name: String): Name = Name(None, None, None, None, Some(name), None, None, None, None, None)

  extension (n: Name)
    def full(v: String): Name = n.copy(full = Some(v))

    /** An honorific before the name, such as `Dr`. */
    def title(v: String): Name = n.copy(title = Some(v))
    def forename(v: String): Name = n.copy(forename = Some(v))

    /** A further forename, where a middle name is recorded separately. */
    def forename2(v: String): Name = n.copy(forename2 = Some(v))
    def surname(v: String): Name = n.copy(surname = Some(v))

    /** A further surname, as the double surnames of Iberian naming carry. */
    def surname2(v: String): Name = n.copy(surname2 = Some(v))

    /** A particle attaching to the surname, such as `de la`, kept apart so
      * sorting can ignore it.
      */
    def prefix(v: String): Name = n.copy(prefix = Some(v))

    /** A generational suffix, such as `II`. */
    def generation(v: String): Name = n.copy(generation = Some(v))

    /** Post-nominal qualifications, such as `CPA`. */
    def credentials(v: String): Name = n.copy(credentials = Some(v))

    /** The locale the name itself belongs to, not the reader's. */
    def locale(v: Locale): Name = n.copy(locale = Some(v))

    /** Whether the name has enough in it to address someone by: a full form, or
      * at least one of the two core fields.
      */
    def usable: Boolean = n.full.nonEmpty || n.forename.nonEmpty || n.surname.nonEmpty
  end extension
end Name

/** An organisation's names: the legal one its register holds, the trading one
  * it puts on a shopfront, and the path of units within it. Instances via
  * [[Organisation$ Organisation]].
  */
final case class Organisation private (legal: String, trading: Option[String], units: Vector[String]) derives CanEqual

/** Construction for [[Organisation]]. */
object Organisation:
  def apply(legal: String): Organisation = Organisation(legal, None, Vector.empty)

  extension (o: Organisation)
    def trading(v: String): Organisation = o.copy(trading = Some(v))

    /** Appends a unit, outermost first: `Organisation("Zensei").unit("Operations")`. */
    def unit(v: String): Organisation = o.copy(units = o.units :+ v)

/** Whoever a document is addressed to - a person, an organisation, or a person
  * at one - with their names, telephone numbers, addresses, email addresses,
  * and registrations on one value. A registration is attached through the
  * [[world.Scheme Scheme]] that issued it and read back the same way, so an
  * invoice can print the authority's own label for it without the application
  * carrying a parallel map of its own. Instances via [[Party$ Party]].
  */
final case class Party private (
  name: Option[Name],
  organisation: Option[Organisation],
  phones: Vector[Phone],
  emails: Vector[Email],
  addresses: Vector[Address],
  identifiers: Vector[Party.Identifier]
) derives CanEqual

/** Construction for [[Party]]: person or organisation entry points, then
  * builders.
  */
object Party:
  /** A registration as held: the scheme that issued it and that scheme's own
    * canonical form of the number.
    */
  final case class Identifier(scheme: Scheme[?], value: String) derives CanEqual
  object Identifier:
    extension (i: Identifier)
      /** What the issuing authority calls this kind of number, for printing
        * beside it.
        */
      def label: String = i.scheme.label

  def apply(name: Name): Party = Party(Some(name), None, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  def apply(organisation: Organisation): Party =
    Party(None, Some(organisation), Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  extension (p: Party)
    def phone(v: Phone): Party = p.copy(phones = p.phones :+ v)
    def email(v: Email): Party = p.copy(emails = p.emails :+ v)
    def address(v: Address): Party = p.copy(addresses = p.addresses :+ v)

    /** The organisation a person belongs to - the company line on their card. */
    def organisation(v: Organisation): Party = p.copy(organisation = Some(v))

    /** The person to contact at an organisation - the attention line on an
      * invoice.
      */
    def name(v: Name): Party = p.copy(name = Some(v))

    /** Attaches a registration under the scheme that issued it, stored in that
      * scheme's canonical form.
      */
    def identifier(s: Scheme[?])(v: Id[s.type]): Party = p.copy(identifiers = p.identifiers :+ Identifier(s, v.value))

    /** The registration this party holds under a scheme, typed to it. Two
      * schemes can print the same label, so the scheme itself is what selects.
      */
    def identifier(s: Scheme[?]): Option[Id[s.type]] =
      p.identifiers.collectFirst { case Identifier(held, v) if held eq s => Id.make[s.type](v) }
  end extension
end Party
