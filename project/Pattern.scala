/** Compiles the pattern subset the curated corpora use into anchored arms of
  * counted character classes - the form world's own matcher runs.
  *
  * The subset is the corpus census, not a choice: digit classes, character
  * classes, literals, alternation, non-capturing and capturing groups, bounded
  * counts, and the optional quantifier. Unbounded repetition appears in no row
  * of any corpus, and anything outside the subset fails the curation rather
  * than reaching a table whose matcher would silently disagree with the source.
  */
object Pattern:

  /** A run of `min` to `max` characters drawn from `set`, in world's own compact
    * class notation (`0-9`, `ABD-H`, a single literal character).
    */
  final case class Seg(set: String, min: Int, max: Int)

  private type Alternatives = Vector[Vector[Item]]

  private enum Item:
    case Class(set: String, min: Int, max: Int)
    case Group(alternatives: Alternatives, optional: Boolean)

  /** Every anchored alternative the pattern admits. An empty result means the
    * pattern was empty - the unconditional case, which each caller reads in its
    * own terms.
    */
  def arms(pattern: String, where: String): Vector[Vector[Seg]] =
    if pattern.isEmpty then Vector.empty
    else
      val (parsed, at) = alternatives(pattern, 0, where)
      if at != pattern.length then sys.error(s"$where: unbalanced group in '$pattern'")
      expand(parsed).map(fuse)

  private def alternatives(pattern: String, from: Int, where: String): (Alternatives, Int) =
    var at = from
    var current = Vector.empty[Item]
    var done = Vector.empty[Vector[Item]]
    while at < pattern.length && pattern.charAt(at) != ')' do
      if pattern.charAt(at) == '|' then
        done = done :+ current
        current = Vector.empty
        at += 1
      else
        val (item, next) = one(pattern, at, where)
        current = current :+ item
        at = next
    (done :+ current, at)
  end alternatives

  private def one(pattern: String, from: Int, where: String): (Item, Int) =
    val (item, at) = pattern.charAt(from) match
      case '(' =>
        val body = if pattern.startsWith("(?:", from) then from + 3 else from + 1
        val (inner, close) = alternatives(pattern, body, where)
        if close >= pattern.length then sys.error(s"$where: unclosed group in '$pattern'")
        (Item.Group(inner, false), close + 1)
      case '[' =>
        val close = classEnd(pattern, from + 1, where)
        (Item.Class(charClass(pattern.substring(from + 1, close), where), 1, 1), close + 1)
      case '\\' =>
        if from + 1 >= pattern.length then sys.error(s"$where: trailing escape in '$pattern'")
        (Item.Class(escaped(pattern.charAt(from + 1), where), 1, 1), from + 2)
      case '*' | '+' | '^' | '$' | '.' | '{' | '?' =>
        sys.error(s"$where: '${pattern.charAt(from)}' is outside the pattern subset, in '$pattern'")
      case literal => (Item.Class(literal.toString, 1, 1), from + 1)
    quantified(item, pattern, at, where)
  end one

  private def quantified(item: Item, pattern: String, from: Int, where: String): (Item, Int) =
    if from >= pattern.length then (item, from)
    else
      pattern.charAt(from) match
        case '?' =>
          item match
            case Item.Class(set, _, _) => (Item.Class(set, 0, 1), from + 1)
            case Item.Group(inner, _)  => (Item.Group(inner, true), from + 1)
        case '{' =>
          val close = pattern.indexOf('}', from)
          if close < 0 then sys.error(s"$where: unclosed count in '$pattern'")
          val (min, max) = pattern.substring(from + 1, close).split(',') match
            case Array(exact)     => (exact.toInt, exact.toInt)
            case Array(low, high) => (low.toInt, high.toInt)
            case _                => sys.error(s"$where: unreadable count in '$pattern'")
          item match
            case Item.Class(set, _, _) => (Item.Class(set, min, max), close + 1)
            // A counted group would multiply out to a product the corpus never asks for.
            case Item.Group(_, _) => sys.error(s"$where: a counted group in '$pattern'")
        case '*' | '+' => sys.error(s"$where: unbounded repetition in '$pattern'")
        case _         => (item, from)

  private def classEnd(pattern: String, from: Int, where: String): Int =
    var at = from
    while at < pattern.length && pattern.charAt(at) != ']' do at += (if pattern.charAt(at) == '\\' then 2 else 1)
    if at >= pattern.length then sys.error(s"$where: unclosed character class in '$pattern'")
    at

  // A class body is already world's compact notation once its escapes are resolved: `x-y` is a
  // range and every other character stands for itself, in the order written.
  private def charClass(body: String, where: String): String =
    if body.startsWith("^") then sys.error(s"$where: a negated character class in '[$body]'")
    var at = 0
    val out = StringBuilder()
    while at < body.length do
      if body.charAt(at) == '\\' then
        out.append(escaped(body.charAt(at + 1), where))
        at += 2
      else
        out.append(body.charAt(at))
        at += 1
    out.result()
  end charClass

  private def escaped(char: Char, where: String): String = char match
    case 'd'                                                                                            => "0-9"
    case '\\' | '-' | '.' | '[' | ']' | '(' | ')' | '|' | '+' | '*' | '?' | '^' | '$' | '{' | '}' | '/' => char.toString
    case other => sys.error(s"$where: the escape '\\$other' is outside the pattern subset")

  private def expand(alternatives: Alternatives): Vector[Vector[Seg]] =
    alternatives.flatMap { sequence =>
      sequence.foldLeft(Vector(Vector.empty[Seg])) { (prefixes, item) =>
        val suffixes = item match
          case Item.Class(set, min, max)   => Vector(Vector(Seg(set, min, max)))
          case Item.Group(inner, optional) =>
            val expanded = expand(inner)
            if optional then Vector.empty[Seg] +: expanded else expanded
        for prefix <- prefixes; suffix <- suffixes yield prefix ++ suffix
      }
    }

  // Adjacent single-character segments over one class fold into one counted segment, which is the
  // same language in fewer segments to match and fewer table entries to carry.
  private def fuse(arm: Vector[Seg]): Vector[Seg] =
    arm.foldLeft(Vector.empty[Seg]) { (done, seg) =>
      done.lastOption match
        case Some(last) if last.set == seg.set =>
          done.init :+ Seg(last.set, last.min + seg.min, last.max + seg.max)
        case _ => done :+ seg
    }
end Pattern
