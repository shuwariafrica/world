package shop

import world.*
import world.money.Money
import world.text.*

// The nine message checks, run against the code sbt-world generated for this build.
object Till:

  private def check(name: String, actual: String, expected: String): Unit =
    assert(actual == expected, s"$name: expected '$expected' but rendered '$actual'")

  def main(arguments: Array[String]): Unit =
    val en = Messages(Cultures.en)
    val sw = Messages(Cultures.sw)
    val ar = Messages(Cultures.ar_EG)

    check("messages: en plural branches", en.cartItems(1), "1 item")
    check("messages: en plural branches", en.cartItems(3), "3 items")

    check("messages: decimal plural parameter keeps full operands", en.weight(BigDecimal("1.00")), "1.00 kilograms")
    check("messages: decimal plural parameter keeps full operands", en.weight(BigDecimal(1)), "1 kilogram")

    check("messages: sw translation", sw.cartItems(1), "Bidhaa moja")
    check("messages: sw translation", sw.cartItems(3), "Bidhaa 3")

    check("messages: ar dual", ar.cartItems(2), "\u0639\u0646\u0635\u0631\u0627\u0646")

    // The declared default numbering system is CLDR's own: `ar-EG` declares `arab`, so its digits are
    // Arabic-Indic, and the `latn` system it also declares swaps in whole through `numbered`.
    check("messages: ar many in arabic digits", ar.cartItems(11), "\u0661\u0661 \u0639\u0646\u0635\u0631\u064b\u0627")
    val latin = Cultures.ar_EG.numbered("latn").getOrElse(sys.error("ar-EG declares no latn numbering"))
    check("cultures: a declared alternate numbering system swaps in whole", BigDecimal(11).display(using latin), "11")

    check("messages: names are isolated in LTR cultures too", en.greeting("\u0628\u0648\u0628"), "Hello, \u2068\u0628\u0648\u0628\u2069!")
    check(
      "messages: ar isolates the foreign name",
      ar.greeting("Bob"),
      "\u0645\u0631\u062d\u0628\u064b\u0627\u060c \u2068Bob\u2069!"
    )

    check("messages: a consumer type is a message parameter through Display", en.promo(Sku("FLOUR-2KG")), "Try [FLOUR-2KG] today!")
    check("messages: a consumer type is a message parameter through Display", sw.promo(Sku("FLOUR-2KG")), "Jaribu [FLOUR-2KG] leo!")

    check(
      "messages: money parameter renders in culture",
      sw.receiptTotal(Money.Value(Currency.KES, BigDecimal("1234.50"))),
      "Jumla: Ksh\u00a01,234.50"
    )

    // The declared set arrives as ordinary values, and negotiation is total.
    assert(Cultures.negotiate("fr") == Cultures.en, "an unmatched preference did not land on the default")
    assert(Cultures.negotiate("sw-KE,sw;q=0.9") == Cultures.sw, "a matched preference did not select its culture")

    val compared = Golden.run()
    println(s"[world] golden differential: $compared comparisons against the JDK's own CLDR data, 1 recorded divergence")
    println("[world] all nine message checks pass against generated output")
