package shop

import java.text.NumberFormat
import java.util.Locale as JavaLocale

import world.*
import world.text.*

// The golden-corpus differential: world's generated cultures against the JDK's own CLDR-backed
// formatter, which is an independent implementation of the same source data. The two do not agree
// everywhere by construction - they carry different CLDR releases, and the JDK applies its own
// fraction-digit defaults - so the differential asserts on the properties both implementations
// define identically: the digits, the grouping separator and its positions, and the decimal
// separator. Every comparison is counted, and a run that compares nothing fails.
object Golden:

  private val cases: Vector[(String, Culture, JavaLocale)] =
    Vector(
      ("en", Cultures.en, JavaLocale.forLanguageTag("en")),
      ("sw", Cultures.sw, JavaLocale.forLanguageTag("sw")),
      ("pl", Cultures.pl, JavaLocale.forLanguageTag("pl")),
      ("ar-EG", Cultures.ar_EG, JavaLocale.forLanguageTag("ar-EG"))
    )

  private val values: Vector[BigDecimal] =
    Vector(BigDecimal(0), BigDecimal(1), BigDecimal(999), BigDecimal(1000), BigDecimal(10000), BigDecimal(1234567))

  // The divergences this corpus expects, each one a place the JDK's formatter does not implement what
  // CLDR states. `pl` declares minimumGroupingDigits 2, so Polish groups only from five integer
  // digits (UTS 35 Part 3, section 2.3): 1000 carries no separator and 10 000 does.
  // java.text.DecimalFormat has no such parameter and groups from four, so it separates where Polish
  // does not.
  private val expected: Set[String] = Set("pl 1000")

  def run(): Int =
    var compared = 0
    var divergent = Vector.empty[String]
    cases.foreach { (tag, culture, java) =>
      given Culture = culture
      val reference = NumberFormat.getIntegerInstance(java)
      values.foreach { value =>
        val ours = value.display
        val theirs = reference.format(value.underlying)
        compared += 1
        if ours != theirs then divergent = divergent :+ s"$tag $value"
      }
    }
    assert(compared > 0, "the golden differential compared nothing")
    val unexpected = divergent.toSet -- expected
    val absent = expected -- divergent.toSet
    assert(unexpected.isEmpty, s"$compared comparisons, new divergences: ${unexpected.mkString(", ")}")
    // A recorded divergence that stops diverging is as much a change as a new one: the JDK may adopt
    // the rule, and the record must follow rather than pass silently.
    assert(absent.isEmpty, s"$compared comparisons, recorded divergences no longer present: ${absent.mkString(", ")}")
    compared
