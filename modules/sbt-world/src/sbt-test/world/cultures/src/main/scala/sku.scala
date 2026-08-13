package shop

import world.text.Display

// A consumer's own type joining presentation as a first-class citizen: one instance in the
// companion, and a Sku is indistinguishable from world's own values in messages and renderers.
final case class Sku(code: String) derives CanEqual

object Sku:
  given Display[Sku] = Display.of((sku, _) => s"[${sku.code}]")
