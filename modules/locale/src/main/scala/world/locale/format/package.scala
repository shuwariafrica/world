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
package world.locale.format

import world.format.Formatter.given
import world.locale.country.Alpha2Code
import world.locale.country.Alpha3Code
import world.locale.country.Country
import world.locale.country.M49Code

/** Alias for [[world.format.Formatter]] */
type Formatter[A] = world.format.Formatter[A]

/** Alias for
  * [[world.format.Formatter world.format.Formatter]]
  */
val Formatter = world.format.Formatter

/** Default formatter for Country: full name */
given Formatter[Country] = world.format.Formatter[Country](_.name)

/** Default formatter for Alpha2Code: uppercase code */
given Formatter[Alpha2Code] = world.format.Formatter[Alpha2Code](_.value)

/** Default formatter for Alpha3Code: uppercase code */
given Formatter[Alpha3Code] = world.format.Formatter[Alpha3Code](_.value)

/** Default formatter for M49Code: numeric string */
given Formatter[M49Code] = world.format.Formatter[M49Code](code => code.value.formatted)
