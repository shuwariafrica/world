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
package world.money

import scala.annotation.targetName

import world.*

/** A proportion stored as its exact fraction, where `Percent(16)` is sixteen
  * percent - so the sixteen-versus-nought-point-one-six confusion cannot be
  * expressed. Instances via [[Percent$ Percent]].
  */
opaque type Percent = BigDecimal

/** Factories and application for [[Percent]]. */
object Percent:
  def apply(value: Int): Percent = BigDecimal(value) / 100
  def apply(value: BigDecimal): Percent = value / 100
  inline def apply(value: Double): Percent =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")
  inline def apply(value: Float): Percent =
    scala.compiletime.error("binary floating-point cannot carry exact amounts; construct from a decimal string or integer")

  /** The markup a price represents over a cost - `(price - cost) / cost` - as a
    * percentage figure rounded to `scale` places by `mode` (the quotient need
    * not terminate, so the boundary is named). The complement of [[margin]]:
    * the two are named apart because confusing them is a real pricing bug
    * class. `Undefined` at zero cost.
    */
  def markup[C <: Currency & Singleton](cost: Money[C], price: Money[C], scale: Int, mode: Rounding): Either[Undefined, Percent] =
    quotient(price.amount - cost.amount, cost.amount, scale, mode)

  /** The margin a price carries - `(price - cost) / price` - as a percentage
    * figure rounded to `scale` places by `mode`. `Undefined` at zero price.
    */
  def margin[C <: Currency & Singleton](cost: Money[C], price: Money[C], scale: Int, mode: Rounding): Either[Undefined, Percent] =
    quotient(price.amount - cost.amount, price.amount, scale, mode)

  /** Exact application, read as commerce does: `Percent(16).of(total)`. */
  def of[C <: Currency & Singleton](p: Percent, m: Money[C]): Money[C] = p.of(m)

  private def quotient(difference: BigDecimal, base: BigDecimal, scale: Int, mode: Rounding): Either[Undefined, Percent] =
    if base.signum == 0 then Left(Undefined)
    else
      val figure =
        BigDecimal((difference * 100).underlying.divide(base.underlying, scale, rounder.jdk(mode)))
      Right(figure / 100)

  extension (p: Percent)
    @targetName("fractionOf") def fraction: BigDecimal = p

    /** The percentage figure, so `Percent(16).value` is 16, in plain form at
      * every magnitude.
      */
    def value: BigDecimal = BigDecimal((p * 100).underlying.stripTrailingZeros.toPlainString)
    @targetName("ext_of")
    def of[C <: Currency & Singleton](m: Money[C]): Money[C] = Money.apply[C](m.amount * p)

  given CanEqual[Percent, Percent] = CanEqual.derived
  given Ordering[Percent] = Ordering.BigDecimal.on(identity)
end Percent

/** A declared tax structure of named components, each levied on the net
  * ([[Tax.on]]), on the net plus other components' charged amounts
  * ([[Tax.over]]), or withheld from the payable ([[Tax.withheld]]).
  *
  * The structure is the application's configuration: no jurisdiction's rates or
  * bases are curated here. A component's base is referenced as a value, so
  * naming an undeclared component cannot be written, and assembly validates the
  * rest. Instances via [[Tax$ Tax]].
  */
final case class Tax private (components: Vector[Tax.Component]) derives CanEqual

/** Component builders, assembly, and application for [[Tax]]. */
object Tax:
  /** One named component of a declared structure. Instances via the
    * [[Tax$ Tax]] builders.
    */
  final case class Component private[Tax] (label: String, rate: Percent, over: Vector[Component], withheld: Boolean) derives CanEqual

  /** Why a structure was refused, each case carrying the offending label. */
  sealed abstract class Invalid(message: String) extends WorldError(message) derives CanEqual
  object Invalid:
    final case class Duplicate(label: String) extends Invalid("duplicate component")
    final case class Unlisted(label: String) extends Invalid("base component not declared before its referent")
    final case class Withheld(label: String) extends Invalid("a withheld component cannot be a base")

  /** A component levied on the net. */
  def on(label: String, rate: Percent): Component = Component(label, rate, Vector.empty, false)

  /** A component levied on the net plus the named components' charged amounts. */
  def over(label: String, rate: Percent, base: Component*): Component =
    Component(label, rate, base.toVector, false)

  /** A withholding component: computed on the net, subtracted from the payable,
    * never part of the gross.
    */
  def withheld(label: String, rate: Percent): Component =
    Component(label, rate, Vector.empty, true)

  // The single-component fast path for the Taxed entry points: base-free by construction.
  private[money] def single(label: String, rate: Percent): Tax =
    new Tax(Vector(Component(label, rate, Vector.empty, false)))

  /** Assembles a structure: labels distinct, every base declared before the
    * component that reads it, and no base withheld - a withheld amount never
    * enters the gross, so cascading over one would read an amount the document
    * does not carry.
    */
  def of(components: Component*): Either[Invalid, Tax] =
    val list = components.toVector
    val labels = list.map(_.label)
    val duplicate = labels.diff(labels.distinct).headOption.map(Invalid.Duplicate(_))
    val unlisted = list.zipWithIndex
      .flatMap((c, i) => c.over.filterNot(list.take(i).contains).map(_.label))
      .headOption
      .map(Invalid.Unlisted(_))
    val held = list.flatMap(_.over.filter(_.withheld)).headOption.map(o => Invalid.Withheld(o.label))
    duplicate.orElse(unlisted).orElse(held).toLeft(new Tax(list))

  /** Prices tax-exclusively, where `amount` is the net. */
  def exclusive[C <: Currency & Singleton](t: Tax, amount: Money[C], mode: Rounding)(using ValueOf[C]): Taxed[C] =
    t.exclusive(amount, mode)

  /** Prices tax-inclusively, where `amount` is the gross. */
  def inclusive[C <: Currency & Singleton](t: Tax, amount: Money[C], mode: Rounding)(using ValueOf[C]): Either[Undefined, Taxed[C]] =
    t.inclusive(amount, mode)

  @targetName("inclusiveAtScale")
  def inclusive[C <: Currency & Singleton](t: Tax, amount: Money[C], scale: Int, mode: Rounding): Either[Undefined, Taxed[C]] =
    t.inclusive(amount, scale, mode)

  // Assembly guarantees every base is declared and none is withheld, so nothing is revalidated
  // here.
  private def charged[C <: Currency & Singleton](components: Vector[Component], net: Money[C], round: Money[C] => Money[C]): Vector[
    (Component, Money[C])] =
    components.foldLeft(Vector.empty[(Component, Money[C])]) { (acc, c) =>
      val base = c.over.foldLeft(net) { (b, o) =>
        b + acc.collectFirst { case (`o`, m) => m }.getOrElse(Money.zero[C])
      }
      acc :+ (c -> round(c.rate.of(base)))
    }

  extension (t: Tax)
    /** Prices tax-exclusively: `amount` is the net, and each component is
      * computed forward from it and rounded at the currency's scale by `mode`.
      * Named for the pricing direction, so that `net` and `gross` keep one
      * reading each across the module.
      */
    @targetName("ext_exclusive")
    def exclusive[C <: Currency & Singleton](amount: Money[C], mode: Rounding)(using ValueOf[C]): Taxed[C] =
      val rows = charged(t.components, amount, _.rounded(mode))
      Taxed(amount, rows.collect { case (c, m) if !c.withheld => c.label -> m }, rows.collect { case (c, m) if c.withheld => c.label -> m })

    /** Prices tax-inclusively: `amount` is the gross, the shelf price. The
      * combined factor is inverted, the components computed forward, and the
      * net reconciled as gross minus their sum - so `net + tax == gross` holds
      * by construction and the rounding remainder lands in the net
      * deterministically. Withholding computes on the reconciled net.
      * `Undefined` where a degenerate structure would divide by zero.
      */
    @targetName("ext_inclusive")
    def inclusive[C <: Currency & Singleton](amount: Money[C], mode: Rounding)(using c: ValueOf[C]): Either[Undefined, Taxed[C]] =
      t.inclusive(amount, c.value.digits.getOrElse(0), mode)

    /** The same extraction at an explicit scale - the controlled form for
      * currencies that record no minor unit, completing the family's
      * explicit-scale pair.
      */
    @targetName("ext_inclusiveAtScale")
    def inclusive[C <: Currency & Singleton](amount: Money[C], scale: Int, mode: Rounding): Either[Undefined, Taxed[C]] =
      val effective = t.components.foldLeft(Map.empty[Component, BigDecimal]) { (acc, c) =>
        acc.updated(c, c.rate.fraction * (BigDecimal(1) + c.over.flatMap(acc.get).sum))
      }
      val inclusive = t.components.filterNot(_.withheld)
      val factor = BigDecimal(1) + inclusive.flatMap(effective.get).sum
      amount.divided(factor, scale, mode).map { provisional =>
        val rows = charged(inclusive, provisional, _.rounded(scale, mode))
        val netAmount = rows.foldLeft(amount)(_ - _._2)
        val held = charged(t.components.filter(_.withheld), netAmount, _.rounded(scale, mode))
        Taxed(netAmount, rows.map((c, m) => c.label -> m), held.map((c, m) => c.label -> m))
      }
    end inclusive
  end extension
end Tax

/** A priced document: the net, the charged amount per component in declaration
  * order, and any withheld amounts, with `net + tax == gross` by construction
  * and the payable derived.
  *
  * A reversal negates the amounts recorded here rather than recomputing them,
  * so a credit note cancels the original rounding exactly. Instances via
  * [[Taxed$ Taxed]] and [[Tax$ Tax]].
  */
final case class Taxed[C <: Currency & Singleton]
  (net: Money[C], components: Vector[(String, Money[C])], withheld: Vector[(String, Money[C])])

/** Single-rate fast paths and accessors for [[Taxed]]. */
object Taxed:
  /** Tax-exclusive single-rate pricing, the dominant till case: one unlabelled
    * component.
    */
  def exclusive[C <: Currency & Singleton](amount: Money[C], rate: Percent, mode: Rounding)(using ValueOf[C]): Taxed[C] =
    Tax.single("", rate).exclusive(amount, mode)

  /** Tax-inclusive single-rate pricing: the net extracted at this boundary, the
    * tax the exact complement.
    */
  def inclusive[C <: Currency & Singleton](amount: Money[C], rate: Percent, mode: Rounding)(using ValueOf[C]): Either[Undefined, Taxed[C]] =
    Tax.single("", rate).inclusive(amount, mode)

  /** One component's charged amount, by its declared label. */
  def apply[C <: Currency & Singleton](t: Taxed[C], label: String): Option[Money[C]] = t(label)

  /** Splits the recorded document by weights, per component. */
  def allocate[C <: Currency & Singleton](t: Taxed[C], weights: Seq[Ratio]): Either[Money.Unallocatable, Vector[Taxed[C]]] =
    t.allocate(weights)

  /** Equal parts of the recorded document. */
  def split[C <: Currency & Singleton](t: Taxed[C], parts: Int): Either[Money.Unallocatable, Vector[Taxed[C]]] = t.split(parts)

  extension [C <: Currency & Singleton](t: Taxed[C])
    /** The tax total across components. */
    def tax: Money[C] = t.components.foldLeft(Money.zero[C])(_ + _._2)
    def gross: Money[C] = t.net + t.tax

    /** What the payer actually pays: gross minus every withheld amount. */
    def payable: Money[C] = t.withheld.foldLeft(t.gross)(_ - _._2)

    @targetName("ext_apply")
    def apply(label: String): Option[Money[C]] =
      t.components.collectFirst { case (l, m) if l == label => m }

    @targetName("negated") def unary_- : Taxed[C] =
      Taxed(-t.net, t.components.map((l, m) => (l, -m)), t.withheld.map((l, m) => (l, -m)))

    /** Splits the recorded document by weights, for a partial refund or a
      * credit note covering part of a line. Every recorded amount allocates
      * independently, so the parts sum back to the whole per component.
      * Recomputing tax on a partial amount could miss the recorded document by
      * a rounding unit; this cannot.
      */
    @targetName("ext_allocate")
    def allocate(weights: Seq[Ratio]): Either[Money.Unallocatable, Vector[Taxed[C]]] =
      def per(m: Money[C]): Either[Money.Unallocatable, Vector[Money[C]]] = m.allocate(weights)
      def sequence(entries: Vector[(String, Money[C])]): Either[Money.Unallocatable, Vector[(String, Vector[Money[C]])]] =
        entries.foldLeft(Right(Vector.empty): Either[Money.Unallocatable, Vector[(String, Vector[Money[C]])]]) { (acc, e) =>
          acc.flatMap(out => per(e._2).map(parts => out :+ (e._1 -> parts)))
        }
      for
        nets <- per(t.net)
        comps <- sequence(t.components)
        held <- sequence(t.withheld)
      yield nets.indices.toVector.map(i => Taxed(nets(i), comps.map((l, parts) => l -> parts(i)), held.map((l, parts) => l -> parts(i))))
    end allocate

    /** Equal parts of the recorded document; the two-way split of a
      * half-returned line.
      */
    @targetName("ext_split")
    def split(parts: Int): Either[Money.Unallocatable, Vector[Taxed[C]]] =
      t.allocate(Vector.fill(parts)(Ratio.One))
  end extension

  given [C <: Currency & Singleton] => CanEqual[Taxed[C], Taxed[C]] = CanEqual.derived
end Taxed

/** A holding of several currencies at once - a cart, a wallet, a multi-currency
  * report - as an exact total per currency. Instances via [[Bag$ Bag]].
  */
opaque type Bag = Map[Currency, BigDecimal]

/** Construction and per-currency totals for [[Bag]]. */
object Bag:
  val empty: Bag = Map.empty

  def apply(values: Money.Value*): Bag = values.foldLeft(empty)(added)

  /** The bag with `v` added to its currency's running total. */
  def added(b: Bag, v: Money.Value): Bag =
    b.updated(v.currency, b.getOrElse(v.currency, BigDecimal(0)) + v.amount)

  @targetName("addedTyped")
  def added[C <: Currency & Singleton](b: Bag, m: Money[C])(using c: ValueOf[C]): Bag =
    added(b, Money.Value(c.value, m.amount))

  // Sums per-currency totals rather than replacing them, which the underlying map's own `++`
  // would do - a merged wallet silently dropping a balance.
  def merged(b: Bag, o: Bag): Bag = o.foldLeft(b)((acc, e) => added(acc, Money.Value(e._1, e._2)))

  /** The total for one currency; zero when the bag holds none of it. */
  def total(b: Bag, c: Currency): Money.Value = Money.Value(c, b.getOrElse(c, BigDecimal(0)))

  extension (b: Bag)
    @targetName("add") def +(v: Money.Value): Bag = added(b, v)
    @targetName("addTyped") def +[C <: Currency & Singleton](m: Money[C])(using ValueOf[C]): Bag =
      added(b, m)
    @targetName("merge") def ++(o: Bag): Bag = merged(b, o)
    @targetName("ext_total") def apply(c: Currency): Money.Value = total(b, c)

    /** Every held total, code-ordered for determinism. */
    def values: Vector[Money.Value] = b.toVector.sortBy(_._1.code).map((c, a) => Money.Value(c, a))
    def currencies: Set[Currency] = b.keySet
    def isEmpty: Boolean = b.isEmpty

  given CanEqual[Bag, Bag] = CanEqual.derived
end Bag
