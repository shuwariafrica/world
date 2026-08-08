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

import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec

import com.sun.management.ThreadMXBean

class AllocationProbeSuite extends munit.FunSuite:

  private val counter: Option[ThreadMXBean] = ManagementFactory.getThreadMXBean match
    case bean: ThreadMXBean if bean.isThreadAllocatedMemorySupported => Some(bean)
    case _                                                           => None

  private val iterations = 200000

  // Written on every control iteration so the allocation escapes the loop: escape analysis
  // scalar-replaces anything it can prove local, and a control that gets optimised away
  // would leave the probe unable to tell a live counter from a dead one.
  private val escaped = AtomicReference[Array[Byte]]()

  /** Bytes allocated per call of `op`, measured after a warm-up pass so class
    * loading and first-call resolution are excluded. The accumulator is what
    * stops the optimiser discarding a call whose result nothing reads.
    */
  private def bytesPerCall(rows: Int)(op: Int => Int): Double =
    val bean = counter.getOrElse(fail("this JVM exposes no per-thread allocation counter, so the probe measured nothing"))
    bean.setThreadAllocatedMemoryEnabled(true)
    val thread = Thread.currentThread.threadId
    // A measurement loop is the one place a counted iteration is the point: recursion here
    // compiles to the same loop and keeps the probe allocation-free itself.
    @tailrec def run(i: Int, acc: Int): Int =
      if i >= iterations then acc else run(i + 1, acc ^ op(i % rows))
    val warm = run(0, 0)
    val before = bean.getThreadAllocatedBytes(thread)
    val sink = run(0, warm)
    val after = bean.getThreadAllocatedBytes(thread)
    assert(after >= before, s"the allocation counter ran backwards (sink $sink)")
    (after - before).toDouble / iterations.toDouble
  end bytesPerCall

  private def report(name: String, perCall: Double): Double =
    println(f"[probe] $name%-46s $perCall%8.3f B/call")
    perCall

  // A zero reading only means something once the counter is known to move: this control
  // allocates on every iteration, so a zero here would mean the probe measured nothing at
  // all rather than that the accessor is free.
  private lazy val control: Double = report
    ("control: a 32-byte array that escapes",
     bytesPerCall(1) { i =>
       val fresh = new Array[Byte](32)
       escaped.set(fresh)
       fresh.length + i
     })

  test("probe: the allocation counter is live") {
    assert(control > 0.0, s"the control allocated $control B/call, so nothing was being measured")
  }

  test("probe: the packed code-column accessors allocate nothing per call") {
    assert(control > 0.0, "the control did not allocate, so this measurement proves nothing")
    val numeric = report("packed.at territoryNumeric", bytesPerCall(tables.territories)(i => packed.at(tables.territoryNumeric, i)))
    val status = report("Territory.status", bytesPerCall(tables.territories)(i => Territory.fromIndex(i).status.ordinal))
    val direction = report("Script.direction", bytesPerCall(tables.scripts)(i => Script.fromIndex(i).direction.ordinal))
    assert
      (numeric == 0.0 && status == 0.0 && direction == 0.0,
       s"packed code accessors allocated: numeric $numeric, status $status, direction $direction")
  }

  // The accessors that build a value are recorded rather than bounded: what a caller pays
  // depends on whether the value escapes at the call site, which the reading below fixes
  // one way and a real caller may fix the other.
  test("probe: the value-building accessors record their measured cost") {
    assert(control > 0.0, "the control did not allocate, so this measurement proves nothing")
    val costs = Vector
      (
        report("Territory.alpha2 (substring)", bytesPerCall(tables.territories)(i => Territory.fromIndex(i).alpha2.length)),
        report("Region.m49 (Option[Int])", bytesPerCall(tables.regions)(i => Region.fromIndex(i).m49.getOrElse(0))),
        report("Territory.week (Week)", bytesPerCall(tables.territories)(i => Territory.fromIndex(i).week.minimalDays)),
        // A curated currency is the integer arm of the union, so reading it crosses a boxing
        // seam the packed table does not cause: the cost is the union's own erasure.
        report("Currency.digits (boxed union arm)", bytesPerCall(tables.currencies)(i => Currency.fromIndex(i).digits.getOrElse(0)))
      )
    assert
      (costs.length == 4 && costs.forall(_ >= 0.0) && escaped.get.length == 32,
       s"the probe recorded ${costs.length} readings: ${costs.mkString(", ")}")
  }
end AllocationProbeSuite
