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

import world.format.Formatter
import world.format.Formatter.given
import world.locale.Locale
import world.locale.country.Alpha2Code
import world.locale.country.Alpha3Code
import world.locale.country.Country
import world.locale.country.M49Code
import world.locale.language.Language
import world.locale.language.LanguageCode
import world.locale.script.Script
import world.locale.script.ScriptCode

import boilerplate.*

/** Default formatter for Country: full name */
given Formatter[Country] = Formatter[Country](_.name)

/** Default formatter for Alpha2Code: uppercase code */
given Formatter[Alpha2Code] = Formatter[Alpha2Code](_.unwrap)

/** Default formatter for Alpha3Code: uppercase code */
given Formatter[Alpha3Code] = Formatter[Alpha3Code](_.unwrap)

/** Default formatter for M49Code: numeric string */
given Formatter[M49Code] = Formatter[M49Code](code => code.unwrap.display)

/** Default formatter for Language: full name */
given Formatter[Language] = Formatter[Language](_.name)

/** Default formatter for LanguageCode: lowercase code */
given Formatter[LanguageCode] = Formatter[LanguageCode](_.unwrap)

/** Default formatter for Script: full name */
given Formatter[Script] = Formatter[Script](_.name)

/** Default formatter for ScriptCode: title case code */
given Formatter[ScriptCode] = Formatter[ScriptCode](_.unwrap)

/** Default formatter for Locale: BCP 47 tag */
given Formatter[Locale] = Formatter[Locale](_.toBcp47)
