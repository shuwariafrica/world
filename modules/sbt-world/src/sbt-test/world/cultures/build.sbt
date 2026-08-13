val worldVersion = sys.props.getOrElse("world.version", sys.error("world.version is not defined"))

scalaVersion := "3.9.0-RC5"

enablePlugins(WorldPlugin)

libraryDependencies += "africa.shuwari" %% "world-text" % worldVersion

worldPackage := "shop"

worldLocales := Seq("en", "sw", "ar-EG", "pl")

@transient lazy val checkGenerated = taskKey[Unit]("The generated cultures render through the library")

@transient lazy val checkStable = taskKey[Unit]("A second generation writes the same bytes")

@transient lazy val checkHidden = taskKey[Unit]("The corpus never reaches the compile classpath")

@transient lazy val checkCompileTime = taskKey[Unit]("The generated sources compile inside their budget")

@transient lazy val checkCanary = taskKey[Unit]("Validated literals compile inside their budget")

checkGenerated := {
  val out = (Compile / runMain).toTask(" shop.Till").value
  out
}

checkStable := {
  val generated = (Compile / sourceManaged).value / "world" / "cultures.scala"
  val before = IO.read(generated)
  val stamp = (Compile / sourceManaged).value / "world" / "cultures.hash"
  IO.delete(stamp)
  worldGenerate.value
  val after = IO.read(generated)
  assert(before == after, "regeneration wrote different bytes")
}

checkHidden := {
  val entries = (Compile / dependencyClasspath).value.map(_.data.toString)
  assert(
    !entries.exists(_.contains("world-data")),
    s"the curated corpus reached the compile classpath: $entries"
  )
}

// The generated sources are large by construction - a culture carries every display name its locale
// declares - so what they cost a consumer's compile is a budgeted figure, not an assumption. The
// gate asserts it TOOK a measurement: a timing that reports nothing reads as a pass.
checkCompileTime := Def.taskDyn {
  val generated = (Compile / sourceManaged).value / "world" / "cultures.scala"
  IO.touch(generated, setModified = true)
  val started = System.nanoTime()
  Def.task {
    val analysis = (Compile / compile).value
    val elapsed = (System.nanoTime() - started) / 1000000L
    val budget = 60000L
    assert(elapsed > 0L, "the compile-time gate took no measurement")
    assert(analysis.readStamps.getAllProductStamps.size > 0, "the compile produced nothing to measure")
    assert(elapsed < budget, s"the generated sources compiled in ${elapsed}ms, over the ${budget}ms budget")
    streams.value.log.info(s"[world] generated sources compiled in ${elapsed}ms of a ${budget}ms budget")
  }
}.value

// The literal canary, by the probe's own method: N literals timed against a same-shape zero-macro
// baseline, and the DIFFERENTIAL is what one literal costs a consumer's compile. Both ends of the
// recorded range are measured - the registry-checking IBAN engine and the far cheaper civil-date
// reader - and each run asserts it took a measurement.
// One literal's cost has to clear this machine's run-to-run compile variance, which is on the order
// of a tenth of a second: the IBAN engine's ~20ms shows at 400 expansions, while the far cheaper
// civil-date reader needs a longer run before its signal leaves the noise.
def canaryCount(kind: String): Int = if kind == "date" then 1500 else 400

// Each run's sources carry their own discriminator: identical inputs are served from sbt's action
// cache, and a cache hit timed against a real compile is not a differential.
def canarySources(directory: File, kind: String, count: Int, tag: String): Unit =
  IO.delete(directory)
  val body = kind match
    case "baseline" =>
      "  inline def mk(inline text: String): String = text" +:
        (1 to count).map(at => s"""  val v$at = mk("GB29 NWBK 6016 1331 9268 19")""").toVector
    case "iban" =>
      "  import world.id.IBAN" +:
        (1 to count).map(at => s"""  val v$at = IBAN("GB29 NWBK 6016 1331 9268 19")""").toVector
    case _ =>
      "  import world.Date" +:
        (1 to count).map(at => s"  val v$at = Date(2026, 7, 23)").toVector
  IO.write(directory / "canary.scala", (s"// run: $tag" +: "package canary" +: "object Literals:" +: body).mkString("\n"))

def timedCompile(kind: String, count: Int, tag: String): Def.Initialize[Task[Long]] = Def.taskDyn {
  canarySources((Compile / scalaSource).value / "canary", kind, count, tag)
  // A cold compile per run, exactly as the probe measured: the class output and the incremental
  // analysis both go, so the two runs differ only by the macro under measurement.
  IO.delete((Compile / classDirectory).value)
  IO.delete((Compile / compileAnalysisTargetRoot).value)
  val started = System.nanoTime()
  Def.task {
    val _ = (Compile / compile).value
    (System.nanoTime() - started) / 1000000L
  }
}

@transient lazy val canaryWarmup = taskKey[Unit]("Warm the compiler before any timing")

@transient lazy val canaryBaseline = taskKey[Unit]("Time the zero-macro baseline at the IBAN sample size")

@transient lazy val canaryBaselineDate = taskKey[Unit]("Time the zero-macro baseline at the Date sample size")

@transient lazy val canaryIban = taskKey[Unit]("Time the IBAN-class literals against the baseline")

@transient lazy val canaryDate = taskKey[Unit]("Time the Date-class literals against the baseline")

def record(name: String): Def.Initialize[File] = Def.setting(target.value / s"canary-$name.txt")

// The first cold compile in a session pays for JIT warm-up, which would land entirely on whichever
// run went first and make its differential meaningless. This run is discarded.
canaryWarmup := {
  val elapsed = timedCompile("baseline", canaryCount("iban"), "warmup").value
  streams.value.log.info(s"[world] canary warm-up compiled in ${elapsed}ms, discarded")
}

// Each family is timed against a baseline of its OWN size: a differential between runs of different
// literal counts would measure the count, not the macro.
canaryBaseline := {
  val small = timedCompile("baseline", canaryCount("iban"), "baseline-small").value
  assert(small > 0L, "the canary baseline took no measurement")
  IO.write(record("iban").value, small.toString)
  streams.value.log.info(s"[world] canary baseline compiled in ${small}ms at ${canaryCount("iban")} expansions")
}

canaryBaselineDate := {
  val large = timedCompile("baseline", canaryCount("date"), "baseline-large").value
  assert(large > 0L, "the canary baseline took no measurement")
  IO.write(record("date").value, large.toString)
  streams.value.log.info(s"[world] canary baseline compiled in ${large}ms at ${canaryCount("date")} expansions")
}

canaryIban := {
  val elapsed = timedCompile("iban", canaryCount("iban"), "iban").value
  val baseline = IO.read(record("iban").value).trim.toLong
  val per = (elapsed - baseline).toDouble / canaryCount("iban").toDouble
  assert(elapsed > 0L && baseline > 0L, "the IBAN canary took no measurement")
  assert(per > 0.0, s"the IBAN differential came out at ${per}ms, so the run measured noise rather than the macro")
  assert(per < 40.0, s"an IBAN literal cost ${per}ms against a 40ms ceiling")
  streams.value.log.info(s"[world] IBAN literals: ${elapsed}ms against a ${baseline}ms baseline, ${per}ms each")
}

canaryDate := {
  val elapsed = timedCompile("date", canaryCount("date"), "date").value
  val baseline = IO.read(record("date").value).trim.toLong
  val per = (elapsed - baseline).toDouble / canaryCount("date").toDouble
  assert(elapsed > 0L && baseline > 0L, "the Date canary took no measurement")
  assert(per > 0.0, s"the Date differential came out at ${per}ms, so the run measured noise rather than the macro")
  assert(per < 12.0, s"a Date literal cost ${per}ms against a 12ms ceiling")
  streams.value.log.info(s"[world] Date literals: ${elapsed}ms against a ${baseline}ms baseline, ${per}ms each")
}
