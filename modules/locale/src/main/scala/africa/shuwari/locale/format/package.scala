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
package africa.shuwari.locale.format

import africa.shuwari.format.Formatter.given

import africa.shuwari.locale.country.Alpha2Code
import africa.shuwari.locale.country.Alpha3Code
import africa.shuwari.locale.country.Country
import africa.shuwari.locale.country.M49Code

/** Alias for [[africa.shuwari.format.Formatter]] */
type Formatter[A] = africa.shuwari.format.Formatter[A]

/** Alias for
  * [[africa.shuwari.format.Formatter africa.shuwari.format.Formatter]]
  */
val Formatter = africa.shuwari.format.Formatter

/** Default formatter for Country: full name */
given Formatter[Country] = africa.shuwari.format.Formatter[Country](_.name)

/** Default formatter for Alpha2Code: uppercase code */
given Formatter[Alpha2Code] = africa.shuwari.format.Formatter[Alpha2Code](_.value)

/** Default formatter for Alpha3Code: uppercase code */
given Formatter[Alpha3Code] = africa.shuwari.format.Formatter[Alpha3Code](_.value)

/** Default formatter for M49Code: numeric string */
given Formatter[M49Code] = africa.shuwari.format.Formatter[M49Code](code => code.value.formatted)
