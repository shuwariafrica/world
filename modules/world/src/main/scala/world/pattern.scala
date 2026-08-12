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

import scala.annotation.tailrec

// The corpora state their structures as patterns, but no pattern engine runs here: the build
// expands every alternation into anchored arms, each a run of counted character classes, leaving a
// walk over packed integers at runtime. One arm is a segment count followed by that many
// class-index, minimum, maximum triples, and a class is the same compact notation a scheme mask
// carries, held once in a shared pool.
private[world] object pattern:

  // An absent pattern matches nothing, which is what a territory with no published range means.
  def matches(s: String, encoded: String, classes: String, offsets: String): Boolean =
    arm(s, encoded, classes, offsets, 0, whole = true)

  // An absent pattern matches everything, which is what a format row with no leading-digit
  // condition means.
  def leads(s: String, encoded: String, classes: String, offsets: String): Boolean =
    encoded.isEmpty || arm(s, encoded, classes, offsets, 0, whole = false)

  @tailrec private def arm
      (
        s: String,
        encoded: String,
        classes: String,
        offsets: String,
        at: Int,
        whole: Boolean
      ): Boolean =
    if at >= encoded.length then false
    else
      val count = encoded.charAt(at).toInt
      if fits(s, encoded, classes, offsets, at + 1, count, whole) then true
      else arm(s, encoded, classes, offsets, at + 1 + count * 3, whole)

  // Greedy with backtracking, as the variable-width segments require: a longer run of one class can
  // starve the next, so every admissible length is tried before the arm is given up on.
  private def fits
      (
        s: String,
        encoded: String,
        classes: String,
        offsets: String,
        from: Int,
        count: Int,
        whole: Boolean
      ): Boolean =
    def loop(pos: Int, seg: Int): Boolean =
      if seg == count then !whole || pos == s.length
      else
        val at = from + seg * 3
        val set = packed.slice(classes, offsets, encoded.charAt(at).toInt)
        (encoded.charAt(at + 1).toInt to encoded.charAt(at + 2).toInt).exists { n =>
          pos + n <= s.length
          && (pos until pos + n).forall(i => schemes.in(s.charAt(i), set))
          && loop(pos + n, seg + 1)
        }
    loop(0, 0)
  end fits
end pattern
