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

import world.Calendar.Buddhist
import world.Calendar.Coptic
import world.Calendar.Ethiopic
import world.Calendar.Gregorian
import world.Calendar.Parts
import world.Calendar.ROC

object Anka extends Calendar.Offset("ANKA", -1000)

class CalendarSuite extends munit.FunSuite:

  test("calendar: one day carries every calendar's labels at once") {
    assert
      (
        Ethiopic.at(Date(2025, 9, 11)) == Parts(2018, 1, 1)
          && Coptic.at(Date(2025, 9, 11)) == Parts(1742, 1, 1)
          && Buddhist.at(Date(2025, 9, 11)) == Parts(2568, 9, 11))
  }

  // The corpus's own anchors decide the direction: Ethiopic 2018 and Coptic 1742 share a
  // day, so the ETHIOPIC number is the larger.
  test("calendar: ethiopic and coptic label one day 276 years apart") {
    assertEquals(Ethiopic.at(Date(2024, 9, 11)).year - Coptic.at(Date(2024, 9, 11)).year, 276)
  }

  test("calendar: buddhist anchors round trip") {
    assert
      (
        Buddhist.of(2569, 1, 1) == Right(Date(2026, 1, 1))
          && Buddhist.of(2484, 1, 1) == Right(Date(1941, 1, 1))
          && Buddhist.at(Date(2026, 1, 1)) == Parts(2569, 1, 1))
  }

  test("calendar: roc anchors round trip") {
    assert
      (
        ROC.of(115, 1, 1) == Right(Date(2026, 1, 1))
          && ROC.of(1, 1, 1) == Right(Date(1912, 1, 1))
          && ROC.at(Date(1912, 1, 1)) == Parts(1, 1, 1))
  }

  test("calendar: ethiopic anchors round trip") {
    assert
      (
        Ethiopic.of(2018, 1, 1) == Right(Date(2025, 9, 11))
          && Ethiopic.of(2017, 1, 1) == Right(Date(2024, 9, 11))
          && Ethiopic.of(1962, 4, 24) == Right(Date(1970, 1, 2))
          && Ethiopic.of(2018, 10, 1) == Right(Date(2026, 6, 8))
          && Ethiopic.at(Date(1970, 1, 2)) == Parts(1962, 4, 24))
  }

  test("calendar: coptic anchors round trip") {
    assert
      (
        Coptic.of(1742, 1, 1) == Right(Date(2025, 9, 11))
          && Coptic.of(1741, 1, 1) == Right(Date(2024, 9, 11))
          && Coptic.at(Date(2024, 9, 11)) == Parts(1741, 1, 1))
  }

  // The 13th month: five days, six in leap years ((year + 1) % 4 == 0) - a short month,
  // never an inserted day.
  test("calendar: the leap sixth of pagumen exists exactly in leap years") {
    assert
      (
        Ethiopic.of(2015, 13, 6) == Right(Date(2023, 9, 11))
          && Ethiopic.at(Date(2023, 9, 11)) == Parts(2015, 13, 6)
          && Ethiopic.of(2016, 13, 6) == Left(Calendar.Invalid("Ethiopic", 2016, 13, 6))
          && Ethiopic.of(2015, 13, 7) == Left(Calendar.Invalid("Ethiopic", 2015, 13, 7))
          && Ethiopic.of(2015, 14, 1) == Left(Calendar.Invalid("Ethiopic", 2015, 14, 1)))
  }

  test("calendar: labelled entry refuses through its own calendar") {
    assertEquals(Buddhist.of(2569, 2, 30), Left(Calendar.Invalid("Buddhist", 2569, 2, 30)))
  }

  test("calendar: gregorian is an instance of the same contract") {
    assert
      (
        Gregorian.of(2026, 7, 23) == Right(Date(2026, 7, 23))
          && Gregorian.at(Date(2026, 7, 23)) == Parts(2026, 7, 23))
  }

  test("calendar: date arithmetic never enters a calendar") {
    assertEquals(Date(2025, 9, 11).plusDays(30).map(Ethiopic.at), Right(Parts(2018, 2, 1)))
  }

  test("calendar: a consumer offset labelling joins as an instance") {
    assert
      (
        Anka.of(3026, 7, 23) == Right(Date(2026, 7, 23))
          && Anka.at(Date(2026, 7, 23)) == Parts(3026, 7, 23))
  }
end CalendarSuite
