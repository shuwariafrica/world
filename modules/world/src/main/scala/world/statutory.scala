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

import scala.annotation.targetName

/** The statutory rule families a deployment declares and its systems consult:
  * what the law requires kept and where (retention and residency), how long a
  * data subject's request may take to answer (response), and what a breach
  * notice owes (breach).
  *
  * The concepts are world's; the rows never are. Statutory tables change by
  * legislation on no schedule, so a deployment supplies its own and every
  * resolution answers with a typed absence rather than a default. Retention,
  * response, and breach rules resolve alike: the rule in force on a day is the
  * latest effective on or before it, and a same-day tie goes to the
  * later-declared row, so a same-day amendment supersedes what it amends.
  * Residency alone carries no effective date, and resolves by territory and
  * record class.
  *
  * One object holds them because `Response` and `Breach` are names a consuming
  * application usually has of its own.
  */
object Statutory:
  /** The instrument and the provision within it - the citation every rule
    * carries, so an answer can be traced to the law that gave it.
    */
  final case class Statute(instrument: String, provision: String) derives CanEqual

  // The one resolution walk the rule families share. The rows in force on the day are those
  // effective on or before it, and the governing row is the latest effective; a same-day tie
  // resolves to the later-declared row, so a same-day amendment supersedes what it amends
  // deterministically rather than by which row a Vector happens to hold first.
  private[world] def latest[R](rows: Vector[R], effective: R => Date, at: Date): Option[R] =
    rows.zipWithIndex
      .filter((r, _) => Ordering[Date].lteq(effective(r), at))
      .maxByOption((r, i) => (effective(r).days, i))
      .map(_._1)

  /** A statutory time limit: hours counted from the moment, or civil days or
    * months counted under the territory's own counting convention. Signed,
    * because a period may run backwards from a date.
    */
  enum Limit derives CanEqual:
    case Hours(count: Int)
    case Days(count: world.Days)
    case Months(count: world.Months)

  /** What a statute requires kept, per territory and record class - the table an
    * erasure consults before it deletes, and the vocabulary a retention policy
    * is written in.
    *
    * The record class and the entity kind are the CONSUMER's own vocabularies,
    * because statutes key on whatever classes they name and distinguish entity
    * kinds only where they choose to; the table is typed over both.
    */
  object Retention:
    /** How long a rule requires: a civil period from the triggering event, a
      * named external event, or whichever of the two ends later. Months carry
      * every surveyed statutory period in whole units.
      */
    enum Term derives CanEqual:
      case Period(months: Months)
      case Event(reference: String)
      case Later(months: Months, reference: String)

    /** One rule, in force from `effective`. `entity` is set only where the
      * statute distinguishes a kind; a rule without one serves every kind.
      */
    final case class Rule[R, E](territory: Territory, record: R, entity: Option[E], term: Term, statute: Statute, effective: Date)
        derives CanEqual

    /** Where records must live: in the territory alone, or anywhere provided a
      * copy that serves a demand is kept there.
      */
    enum Mode derives CanEqual:
      case Exclusive, ServingCopy

    /** Where a territory's records must live. `records` names the classes the
      * statute binds, and is absent where the statute binds all of them.
      */
    final case class Residency[R](territory: Territory, records: Option[Set[R]], mode: Mode, statute: Statute) derives CanEqual

    final case class Table[R, E](rules: Vector[Rule[R, E]], residencies: Vector[Residency[R]]) derives CanEqual

    def rule[R, E](t: Table[R, E], territory: Territory, record: R, entity: E, at: Date)(using CanEqual[R, R], CanEqual[E, E]): Option[
      Rule[R, E]] = t.rule(territory, record, entity, at)

    def residency[R, E](t: Table[R, E], territory: Territory, record: R)(using CanEqual[R, R]): Option[Residency[R]] = t.residency
      (territory, record)

    extension [R, E](t: Table[R, E])
      /** The rule in force for the record class in the territory on the day: the
        * entity-kinded rule where the statute distinguishes one, otherwise the
        * general rule, and among those the latest effective on or before the day,
        * a same-day tie going to the later-declared row. `None` is the typed
        * absence - no rule at all, never a default period.
        */
      @targetName("ext_rule")
      def rule(territory: Territory, record: R, entity: E, at: Date)(using CanEqual[R, R], CanEqual[E, E]): Option[Rule[R, E]] =
        val applicable = t.rules.filter
          (x => x.territory == territory && x.record == record && x.entity.forall(_ == entity))
        val specific = applicable.filter(_.entity.isDefined)
        latest(if specific.nonEmpty then specific else applicable, _.effective, at)

      /** The residency rule binding the record class in the territory, if one
        * does.
        */
      @targetName("ext_residency")
      def residency(territory: Territory, record: R)(using CanEqual[R, R]): Option[Residency[R]] =
        t.residencies.find(x => x.territory == territory && x.records.forall(_.contains(record)))
    end extension
  end Retention

  /** What a statute allows a controller to answer a data subject's request in -
    * the table a request ledger clocks against.
    */
  object Response:
    /** The request kinds the statutes name. */
    enum Kind derives CanEqual:
      case Access, Portability, Erasure, Rectification, Restriction, Objection

    /** Something a controller may require before it answers: proof of identity,
      * or a fee.
      */
    enum Condition derives CanEqual:
      case Identity, Fee

    /** How the initial limit may be extended: by a further fixed limit, or by a
      * period determined case by case with the supervisory authority.
      */
    enum Extension derives CanEqual:
      case Fixed(by: Limit)
      case Determined

    /** One rule, in force from `effective`. `waits` says the clock starts only
      * once every condition is met, which is what separates a jurisdiction whose
      * relevant time is the latest of receipt, identity and fee from one whose
      * days run from receipt regardless; `pausable` says a request for further
      * information stops a clock already running. `refusal` is the separate
      * limit for a refusal notice, where a statute sets one.
      */
    final case class Rule
      (territory: Territory,
       kind: Kind,
       initial: Limit,
       conditions: Set[Condition],
       waits: Boolean,
       pausable: Boolean,
       extension: Option[Extension],
       refusal: Option[Limit],
       statute: Statute,
       effective: Date)
        derives CanEqual

    final case class Table(rules: Vector[Rule]) derives CanEqual

    def rule(t: Table, territory: Territory, kind: Kind, at: Date): Option[Rule] = t.rule(territory, kind, at)

    extension (t: Table)
      /** The rule in force for the request kind in the territory on the day - the
        * latest effective on or before it, a same-day tie going to the
        * later-declared row. `None` is the typed absence.
        */
      @targetName("ext_rule")
      def rule(territory: Territory, kind: Kind, at: Date): Option[Rule] =
        latest(t.rules.filter(x => x.territory == territory && x.kind == kind), _.effective, at)
  end Response

  /** What a statute requires of a personal-data breach notice - the clocks an
    * incident runs against, and the risk at which each notice is owed.
    */
  object Breach:
    /** The risk thresholds the statutes name: a notice owed unless the breach is
      * unlikely to result in a risk, or owed only at a high risk.
      */
    enum Risk derives CanEqual:
      case Unlikely, Likely, High

    /** One rule, in force from `effective`. `processor` and `authority` are the
      * limits to tell the controller and to tell the supervisory authority, each
      * absent where the statute sets no number - "without undue delay" has none.
      * `authorityAt` and `subjectAt` are the risks at which those notices and
      * the communication to the data subjects become owed.
      */
    final case class Rule
      (territory: Territory,
       processor: Option[Limit],
       authority: Option[Limit],
       authorityAt: Risk,
       subjectAt: Risk,
       statute: Statute,
       effective: Date)
        derives CanEqual

    final case class Table(rules: Vector[Rule]) derives CanEqual

    def rule(t: Table, territory: Territory, at: Date): Option[Rule] = t.rule(territory, at)

    extension (t: Table)
      /** The rule in force in the territory on the day - the latest effective on
        * or before it, a same-day tie going to the later-declared row. `None` is
        * the typed absence.
        */
      @targetName("ext_rule")
      def rule(territory: Territory, at: Date): Option[Rule] =
        latest(t.rules.filter(x => x.territory == territory), _.effective, at)
  end Breach
end Statutory
