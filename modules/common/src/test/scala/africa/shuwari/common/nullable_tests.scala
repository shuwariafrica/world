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
// scalafix:off DisableSyntax.null
package africa.shuwari.common

import munit.FunSuite

import africa.shuwari.common.nullable.*

class NullableUtilitiesSuite extends FunSuite:

  // Tests for (A | Null) extensions

  test("toOption should convert non-null value to Some") {
    val value: String | Null = "test"
    assertEquals(value.toOption, Some("test"))
  }

  test("toOption should convert null to None") {
    val value: String | Null = null
    val result = value.toOption
    assert(result.isEmpty)
  }

  test("toEither should convert non-null value to Right") {
    val value: Int | Null = 42
    val result = value.toEither("error")
    assert(result.isRight)
    result.foreach(v => assertEquals(v, 42))
  }

  test("toEither should convert null to Left with custom error") {
    val value: String | Null = null
    val result = value.toEither("Value was null")
    assert(result.isLeft)
    result.left.foreach(e => assertEquals(e, "Value was null"))
  }

  test("mapOption should apply function to non-null value") {
    val value: String | Null = "hello"
    val result = value.mapOption(_.toUpperCase)
    assertEquals(result, Some("HELLO"))
  }

  test("mapOption should return None for null value") {
    val value: String | Null = null
    val result = value.mapOption(_.toUpperCase)
    assert(result.isEmpty)
  }

  test("flatMapOption should apply function and flatten for non-null value") {
    val value: String | Null = "5"
    val result = value.flatMapOption(s => s.toIntOption)
    assertEquals(result, Some(5))
  }

  test("flatMapOption should return None for null value") {
    val value: String | Null = null
    val result = value.flatMapOption(s => s.toIntOption)
    assert(result.isEmpty)
  }

  test("flatMapOption should propagate None from function") {
    val value: String | Null = "not-a-number"
    val result = value.flatMapOption(s => s.toIntOption)
    assert(result.isEmpty)
  }

  // Tests for Option[A | Null] extensions

  test("flattenNull should convert Some(non-null) to Some") {
    val value: Option[String | Null] = Some("test")
    val result = value.flattenNull
    assert(result.isDefined)
    result.foreach(v => assertEquals(v, "test"))
  }

  test("flattenNull should convert Some(null) to None") {
    val value: Option[String | Null] = Some(null)
    val result = value.flattenNull
    assert(result.isEmpty)
  }

  test("flattenNull should convert None to None") {
    val value: Option[String | Null] = None
    val result = value.flattenNull
    assert(result.isEmpty)
  }

  test("mapFlattenNull should apply function to non-null value") {
    val value: Option[String | Null] = Some("hello")
    val result = value.mapFlattenNull(_.length)
    assert(result.isDefined)
    result.foreach(v => assertEquals(v, 5))
  }

  test("mapFlattenNull should return None for Some(null)") {
    val value: Option[String | Null] = Some(null)
    val result = value.mapFlattenNull(_.length)
    assert(result.isEmpty)
  }

  test("mapFlattenNull should return None for None") {
    val value: Option[String | Null] = None
    val result = value.mapFlattenNull(_.length)
    assert(result.isEmpty)
  }

  test("flatMapFlattenNull should apply function and flatten for non-null value") {
    val value: Option[String | Null] = Some("10")
    val result = value.flatMapFlattenNull(s => s.toIntOption)
    assert(result.isDefined)
    result.foreach(v => assertEquals(v, 10))
  }

  test("flatMapFlattenNull should return None for Some(null)") {
    val value: Option[String | Null] = Some(null)
    val result = value.flatMapFlattenNull(s => s.toIntOption)
    assert(result.isEmpty)
  }

  test("flatMapFlattenNull should return None for None") {
    val value: Option[String | Null] = None
    val result = value.flatMapFlattenNull(s => s.toIntOption)
    assert(result.isEmpty)
  }

  test("flatMapFlattenNull should propagate None from function") {
    val value: Option[String | Null] = Some("not-a-number")
    val result = value.flatMapFlattenNull(s => s.toIntOption)
    assert(result.isEmpty)
  }

  // Real-world scenario tests

  test("chaining nullable operations should work correctly") {
    val value: String | Null = "42"
    val result = value.toOption
      .flatMap(_.toIntOption)
      .map(_ * 2)
    assertEquals(result, Some(84))
  }

  test("null in chain should short-circuit") {
    val value: String | Null = null
    val result = value.toOption
      .flatMap(_.toIntOption)
      .map(_ * 2)
    assert(result.isEmpty)
  }

  test("Java interop scenario - System property handling") {
    // Simulating System.getProperty which returns String | Null
    val existingProp: String | Null = "someValue"
    val missingProp: String | Null = null

    val existingResult = existingProp.toOption
    assert(existingResult.isDefined)
    existingResult.foreach(v => assertEquals(v, "someValue"))
    assert(missingProp.toOption.isEmpty)
    val eitherResult = missingProp.toEither("Property not found")
    assert(eitherResult.isLeft)
    eitherResult.left.foreach(e => assertEquals(e, "Property not found"))
  }

  test("Option[A | Null] from map operation should flatten correctly") {
    val map: Map[String, String | Null] = Map("key1" -> "value1", "key2" -> null)

    val result1 = map.get("key1").flattenNull
    val result2 = map.get("key2").flattenNull
    val result3 = map.get("key3").flattenNull

    assert(result1.isDefined)
    result1.foreach(v => assertEquals(v, "value1"))
    assert(result2.isEmpty) // null value filtered out
    assert(result3.isEmpty) // key not found
  }

end NullableUtilitiesSuite
