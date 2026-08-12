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
package consumer

import world.*
import world.Scheme.Check
import world.Scheme.Fold
import world.Scheme.Norm
import world.Scheme.Row
import world.Scheme.Rules
import world.Scheme.Seg

// A consumer's own declarations, outside world's packages: the library ships the scheme CONCEPT and
// no authority rows, so every row below is written here exactly as a consuming application writes
// its own, and the suites verify the concept through them.

// Kenya's KRA PIN: an entity-class letter, nine digits, a trailing letter. The authority publishes
// no check algorithm, so the row asserts structure alone rather than inventing arithmetic.
object KRAPin extends Scheme[Scheme.Tax](Authority("Kenya Revenue Authority"), "KRA PIN"):
  enum Kind derives CanEqual:
    case Individual, Entity

  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector(Seg.Run("AP", 1, 1), Seg.Run("0-9", 9, 9), Seg.Run("A-Z", 1, 1)),
      Check.None
    )
  protected val active: Rules = rules

  extension (p: Id[KRAPin.type]) def kind: Kind = if p.value.head == 'A' then Kind.Individual else Kind.Entity
end KRAPin

type KRAPin = KRAPin.type

// An EU VAT number. The scheme's state codes are scheme-scoped rather than ISO: Greece is `EL` by
// legal mandate and Northern Ireland is `XI`, and each member state carries its own row.
object EUVat extends Scheme[Scheme.Tax](Authority("European Union"), "VAT"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector
        (
          Row("DE", Vector(Seg.Text("DE"), Seg.Run("0-9", 9, 9)), Check.Mod1110),
          Row("EL", Vector(Seg.Text("EL"), Seg.Run("0-9A-Z", 1, 30)), Check.None),
          Row("XI", Vector(Seg.Text("XI"), Seg.Run("0-9A-Z", 1, 30)), Check.None)
        )
    )
  protected val active: Rules = rules

  extension (v: Id[EUVat.type])
    def scheme: String = v.value.take(2)
    def territory: Option[Territory] = Territory.from(v.value.take(2)).toOption
end EUVat

type EUVat = EUVat.type

// Ghana's GRA TIN: an entity class, the literal `00`, seven serial digits, and a weighted mod-11
// check rendered `X` at ten.
object GRATin extends Scheme[Scheme.Tax](Authority("Ghana Revenue Authority"), "GRA TIN"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector(Seg.Run("PCGQV", 1, 1), Seg.Text("00"), Seg.Run("0-9", 7, 7), Seg.Run("0-9X", 1, 1)),
      Check.Weighted(Vector(1, 2, 3, 4, 5, 6, 7, 8, 9), 11)
    )
  protected val active: Rules = rules

type GRATin = GRATin.type

// South Africa's income-tax reference number: ten digits, the first in 0, 1, 2, 3 or 9, Luhn
// checked - the algorithm SARS itself publishes.
object SARSTin extends Scheme[Scheme.Tax](Authority("South African Revenue Service"), "SARS TAX REF"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Preserve),
      Vector(Seg.Run("01239", 1, 1), Seg.Run("0-9", 9, 9)),
      Check.Luhn
    )
  protected val active: Rules = rules

type SARSTin = SARSTin.type

// South Africa's national identity number: thirteen digits, Luhn checked. Only STRUCTURE is
// asserted - the date stem is bounds-checked without a century, because no century rule is
// published and deriving one would invent data.
object ZAId extends Scheme[Scheme.National](Authority("Department of Home Affairs"), "ZA ID"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Preserve),
      Vector(Seg.Run("0-9", 2, 2), Seg.Number(2, 1, 12), Seg.Number(2, 1, 31), Seg.Run("0-9", 7, 7)),
      Check.Luhn
    )
  protected val active: Rules = rules

type ZAId = ZAId.type

// A scheme whose check is code under a consumer-minted kind: the kind vocabulary is open. It parses
// at runtime like any row, and its literal form is honestly refused, because code cannot run while
// compiling.
trait Permit extends Scheme.Kind

object Coded extends Scheme[Permit](Authority("Example Authority"), "CODED"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector(Seg.Run("A-Z", 2, 2), Seg.Run("0-9", 2, 2)),
      Check.Rule(s => s(0) == s(1))
    )
  protected val active: Rules = rules

type Coded = Coded.type

// The Tanzania pair, both attested with unpublished structures, distinguished by issuing authority
// alone: one ISO territory, two revenue authorities, and no code anywhere to key on.
object TraTin extends Scheme[Scheme.Tax](Authority("Tanzania Revenue Authority"), "TIN"):
  protected inline def rules: Rules = Rules(Norm(" -", Fold.Upper), Vector(Seg.Run("0-9", 1, 32)), Check.None)
  protected val active: Rules = rules

object Ztn extends Scheme[Scheme.Tax](Authority("Zanzibar Revenue Authority"), "ZTN"):
  protected inline def rules: Rules = Rules(Norm(" -", Fold.Upper), Vector(Seg.Run("0-9A-Z", 1, 32)), Check.None)
  protected val active: Rules = rules

// A registration scheme world does not curate, declared as the same row shape: parse, the canonical
// value, the literal, kind-bounded slotting, and the party seam all come from the concept.
object UraTin extends Scheme[Scheme.Tax](Authority("Uganda Revenue Authority"), "URA TIN"):
  protected inline def rules: Rules = Rules(Norm(" ", Fold.Preserve), Vector(Seg.Run("0-9", 10, 10)), Check.None)
  protected val active: Rules = rules

type UraTin = UraTin.type

// A second scheme deliberately sharing EUVat's document label: the label-collision case the
// scheme-keyed Party seam exists for.
object HmrcVat extends Scheme[Scheme.Tax](Authority("HM Revenue and Customs"), "VAT"):
  protected inline def rules: Rules = Rules
    (
      Norm(" ", Fold.Upper),
      Vector(Seg.Text("GB"), Seg.Run("0-9", 9, 9)),
      Check.None
    )
  protected val active: Rules = rules

type HmrcVat = HmrcVat.type

// Register values a consumer composes; nothing here is privileged.
object Schemes:
  val tax: Register[Territory, Scheme.Tax] =
    Register(Territory.KE -> KRAPin, Territory.ZA -> SARSTin, Territory.DE -> EUVat)

  val national: Register[Territory, Scheme.National] = Register(Territory.ZA -> ZAId)

// A Zanzibar business's own operating model: the coordinate type is the consumer's, because the
// real world codes nothing below the territory here.
enum Operation derives CanEqual:
  case Mainland, Zanzibar

// A library author's slot demanding any tax registration, whatever scheme issued it.
def taxSlot[S <: Scheme[Scheme.Tax] & Singleton](v: Id[S]): String = v.value

val operations: Register[Operation, Scheme.Tax] =
  Register(Operation.Mainland -> TraTin, Operation.Zanzibar -> TraTin, Operation.Zanzibar -> Ztn)
