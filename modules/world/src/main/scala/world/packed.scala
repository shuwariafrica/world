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

import boilerplate.nullable.*

// One string constant per column: one constant-pool entry each, where the array literal it
// replaces would emit per-element initialisation into a class initialiser.
private[world] object packed:

  inline def at(column: String, index: Int): Int = column.charAt(index).toInt

  // Zero is absence under this encoding, so a stored `n + 1` carries the value `n`.
  inline def optional(column: String, index: Int): Option[Int] =
    val stored = column.charAt(index).toInt
    if stored == 0 then None else Some(stored - 1)

  // Entries shorter than the column's width are right-padded with spaces.
  def code(column: String, width: Int, index: Int): String =
    val start = index * width
    @tailrec def trimmed(at: Int): Int =
      if at > start && column.charAt(at - 1) == ' ' then trimmed(at - 1) else at
    column.substring(start, trimmed(start + width)).unsafe

  // -1 for no such row.
  def indexOf(column: String, width: Int, count: Int, value: String): Int =
    if value.length > width then -1
    else
      @tailrec def scan(i: Int): Int =
        if i >= count then -1 else if holds(column, width, i, value) then i else scan(i + 1)
      scan(0)

  def indexOf(column: String, count: Int, value: Int): Int =
    @tailrec def scan(i: Int): Int =
      if i >= count then -1 else if column.charAt(i).toInt == value then i else scan(i + 1)
    scan(0)

  private def holds(column: String, width: Int, index: Int, value: String): Boolean =
    val start = index * width
    @tailrec def padded(at: Int): Boolean =
      at >= start + width || (column.charAt(at) == ' ' && padded(at + 1))
    column.regionMatches(start, value, 0, value.length) && padded(start + value.length)

  // Located by the companion offset column, which holds one more entry than the column has rows.
  def slice(column: String, offsets: String, index: Int): String =
    column.substring(offsets.charAt(index).toInt, offsets.charAt(index + 1).toInt).unsafe
end packed
