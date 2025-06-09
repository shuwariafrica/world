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
package africa.shuwari.locale.country

/** A trait representing a country or area with its primary identifiers.
  *
  * This trait provides a type-safe representation of a country, bundling its
  * common name with its standardised codes. Concrete instances are not
  * typically created directly, but are accessed as predefined singleton objects
  * from the [[Countries$]] object (e.g., `Countries.KE`).
  *
  * @see [[https://unstats.un.org/unsd/methodology/m49/ UN M49 Standard]]
  * @see [[https://www.iso.org/iso-3166-country-codes.html ISO 3166-1]]
  */
trait Country extends Product with Serializable derives CanEqual:
  /** The common English short name of the country or area (e.g., "Kenya"). */
  def name: String
  /** The [[Alpha2Code]] (ISO 3166-1 Alpha-2), e.g., "KE". */
  def alpha2: Alpha2Code
  /** The [[Alpha3Code]] (ISO 3166-1 Alpha-3), e.g., "KEN". */
  def alpha3: Alpha3Code
  /** The [[M49Code]] (UN M49 numeric code), e.g., 404. */
  def m49: M49Code

  /** The common name of the country, suitable for display purposes. */
  override def toString: String = name
end Country
