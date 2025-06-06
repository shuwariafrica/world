/****************************************************************
 * Copyright © Shuwari Africa Ltd.                              *
 *                                                              *
 * This file is licensed to you under the terms of the Apache   *
 * License Version 2.0 (the "License"); you may not use this    *
 * file except in compliance with the License. You may obtain   *
 * a copy of the License at:                                    *
 *                                                              *
 *     https://www.apache.org/licenses/LICENSE-2.0              *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, *
 * either express or implied. See the License for the specific  *
 * language governing permissions and limitations under the     *
 * License.                                                     *
 ****************************************************************/
package africa.shuwari.money

import scala.annotation.targetName

/** Internal utility extensions, primarily for handling nullable values
  * gracefully within the `africa.shuwari.money` library.
  *
  * These utilities are not intended for public API usage.
  */
private[money] object internal:

  extension [A](v: A | Null)
    @targetName("any_nopt") transparent inline def nopt: Option[A] =
      inline v match
        case valueA: A => Some(valueA) // Type test ensures 'valueA' is not null
        case _: Null   => None

    @targetName("any_noptm") transparent inline def noptM[B](f: A => B): Option[B] = v.nopt.map(f)

    @targetName("any_noptf") transparent inline def noptF[B](f: A => Option[B]): Option[B] = v.nopt.flatMap(f)

  extension [A](v: Option[A | Null])
    @targetName("option_nopt") transparent inline def nopt: Option[A] = v.flatMap {
      case valueA: A => Some(valueA) // Type test ensures 'valueA' is not null
      case _: Null   => None
    }

    @targetName("option_noptm") transparent inline def noptM[B](f: A => B): Option[B] = v.nopt.map(f)

    @targetName("option_noptf") transparent inline def noptF[B](f: A => Option[B]): Option[B] = v.nopt.flatMap(f)
end internal
