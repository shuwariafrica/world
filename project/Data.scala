import sbt.*
import sbt.Keys.*

/** Build integration for the curated datasets: the tasks that regenerate and
  * gate them, and the generators that compile them into each module's packed
  * tables.
  *
  * `Curate`, `Curated`, and `Pack` hold the logic and know nothing of sbt; this
  * object is the only place the two meet.
  */
object Data:

  val curate =
    taskKey[Unit]("Regenerate the curated datasets from their pinned upstream releases.")

  val dataVerify =
    taskKey[Unit]("Verify every curated dataset's provenance, pin, and redistribution terms.")

  val dataReport =
    taskKey[Unit]("Report each curated dataset's row count, pin, and verified terms.")

  val budgets =
    taskKey[Unit]("Report the packed data's measured size against its recorded budget.")

  /** The dataset tasks, for the module that owns the curated files. */
  def curation: List[Setting[?]] = List(
    // These read and write the working tree rather than the task's declared inputs, so they opt
    // out of the build cache: a cached hit would report on a tree it never looked at.
    curate := Def.uncached(Curate.run(root.value, target.value / "curation", streams.value.log)),
    dataVerify := Def.uncached(Curated.verify(root.value, streams.value.log)),
    dataReport := Def.uncached(Curated.report(root.value, streams.value.log))
  )

  /** The territory, language, script, and currency registers, packed and
    * size-gated.
    */
  def registers: List[Setting[?]] = packed(Pack.generate, registerBudgets)

  /** Legal tender, cash practice, and the fixed euro factors, packed and
    * size-gated.
    */
  def monetary: List[Setting[?]] = packed(Pack.money, monetaryBudgets)

  /** The telephone plans with their presentation formats and mobile ranges, and
    * the ISO 13616 registry as scheme rows, packed and size-gated.
    */
  def identifiers: List[Setting[?]] = packed(Pack.identity, identifierBudgets) ++ List(
    Test / sourceGenerators += Def.task {
      Pack.examples(root.value, (Test / sourceManaged).value, streams.value.log)
    }.taskValue
  )

  /** The per-territory structural addressing rules, packed and size-gated. */
  def addressing: List[Setting[?]] = packed(Pack.addressing, addressingBudgets)

  // Re-base on a measured run plus a tenth of headroom.
  private def registerBudgets = Map(
    "tables.scala" -> 185000L,
    "constants.scala" -> 40000L,
    "classfiles" -> 655000L,
    "nir" -> 745000L
  )

  private def monetaryBudgets = Map(
    "tables.scala" -> 13300L,
    "classfiles" -> 484000L,
    "nir" -> 541000L
  )

  private def identifierBudgets = Map(
    "tables.scala" -> 351000L,
    "ibanrules.scala" -> 14400L,
    "classfiles" -> 319000L,
    "nir" -> 386000L
  )

  private def addressingBudgets = Map(
    "tables.scala" -> 45300L,
    "classfiles" -> 172000L,
    "nir" -> 198000L
  )

  private def root = Def.setting((ThisBuild / baseDirectory).value)

  private def packed(generate: (File, File, Logger) => Seq[File], limits: Map[String, Long]): List[Setting[?]] = List(
    // Verification runs ahead of generation, so a dataset reaches an artefact only once its
    // provenance and terms are verified.
    Compile / sourceGenerators += Def.task {
      Curated.verify(root.value, streams.value.log)
      generate(root.value, (Compile / sourceManaged).value, streams.value.log)
    }.taskValue,
    // Depends on the compile that produces them: reading the paths alone measured a stale row
    // silently, and a size gate reporting yesterday's artefact is not a size gate.
    budgets := Def.uncached(
      Def.taskDyn {
        val _ = (Compile / compile).value
        Def.task(
          Pack.budgets(
            (Compile / classDirectory).value,
            ((Compile / sourceManaged).value ** "*.scala").get(),
            limits,
            virtualAxes.value.contains(VirtualAxis.native),
            streams.value.log
          )
        )
      }.value
    )
  )
end Data
