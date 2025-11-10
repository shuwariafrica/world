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
package world.money

/** Internal utility extensions for the `world.money` library.
  *
  * This object re-exports canonical null handling utilities from the common
  * module for use within the money module.
  *
  * @note These utilities are not intended for public API usage.
  */
private[money] object internal:

  // Re-export nullable utilities from common module with descriptive names
  export world.common.nullable.{
    flatMapFlattenNull as nullableFlatMapFlattenNull,
    flatMapOption as nullableFlatMapOption,
    flattenNull as nullableFlattenNull,
    mapFlattenNull as nullableMapFlattenNull,
    mapOption as nullableMapOption,
    toEither as nullableToEither,
    toOption as nullableToOption
  }
end internal
