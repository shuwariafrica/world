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
package world.common

import scala.annotation.targetName

/** Utilities for handling nullable values safely in contexts with explicit
  * nulls enabled.
  *
  * These utilities provide a canonical way to eliminate null at API boundaries,
  * particularly when interoperating with Java libraries or parsing external
  * data.
  *
  * @example
  *   {{{
  * import world.common.nullable.*
  *
  * val maybeNull: String | Null = System.getProperty("user.name")
  * val userName: Option[String] = maybeNull.toOption
  *
  * val result: Either[String, Int] = maybeNull.toEither("Property not set")
  *   }}}
  */
object nullable:

  extension [A](value: A | Null)
    /** Converts a nullable value to an Option, eliminating null.
      *
      * @return Some(value) if non-null, None otherwise.
      */
    @targetName("nullable_toOption")
    transparent inline def toOption: Option[A] =
      // scalafix:off
      if value.asInstanceOf[AnyRef] eq null then None
      else Some(value.asInstanceOf[A])
      // scalafix:on

    /** Converts a nullable value to an Either.
      *
      * @param leftError The error value to use if the value is null.
      * @return Right(value) if non-null, Left(leftError) otherwise.
      */
    @targetName("nullable_toEither")
    transparent inline def toEither[E](leftError: E): Either[E, A] =
      // scalafix:off
      if value.asInstanceOf[AnyRef] eq null then Left(leftError)
      else Right(value.asInstanceOf[A])
      // scalafix:on

    /** Maps a nullable value through a function, returning None if null.
      *
      * @param f The function to apply if the value is non-null.
      * @return Some(f(value)) if non-null, None otherwise.
      */
    @targetName("nullable_mapOption")
    transparent inline def mapOption[B](f: A => B): Option[B] =
      value.toOption.map(f)

    /** FlatMaps a nullable value through a function returning Option.
      *
      * @param f The function to apply if the value is non-null.
      * @return f(value) if non-null, None otherwise.
      */
    @targetName("nullable_flatMapOption")
    transparent inline def flatMapOption[B](f: A => Option[B]): Option[B] =
      value.toOption.flatMap(f)
  end extension

  extension [A](optValue: Option[A | Null])
    /** Converts Option[A | Null] to Option[A], eliminating nested nulls.
      *
      * This is useful when combining Option-returning operations with nullable
      * values from Java interop.
      *
      * @return Option[A] with null values filtered out.
      */
    @targetName("option_nullable_flatten")
    transparent inline def flattenNull: Option[A] = optValue match
      case Some(value) =>
        // scalafix:off
        if value.asInstanceOf[AnyRef] eq null then None
        else Some(value.asInstanceOf[A])
        // scalafix:on
      case _ => None

    /** Maps through a function and flattens null values.
      *
      * @param f The function to apply to non-null values.
      * @return Option[B] with nulls filtered out.
      */
    @targetName("option_nullable_mapFlat")
    transparent inline def mapFlattenNull[B](f: A => B): Option[B] = optValue match
      case Some(value) =>
        // scalafix:off
        if value.asInstanceOf[AnyRef] eq null then None
        else Some(f(value.asInstanceOf[A]))
        // scalafix:on
      case _ => None

    /** FlatMaps through a function returning Option and flattens null values.
      *
      * @param f The function to apply to non-null values.
      * @return Option[B] with nulls filtered out.
      */
    @targetName("option_nullable_flatMapFlat")
    transparent inline def flatMapFlattenNull[B](f: A => Option[B]): Option[B] = optValue match
      case Some(value) =>
        // scalafix:off
        if value.asInstanceOf[AnyRef] eq null then None
        else f(value.asInstanceOf[A])
        // scalafix:on
      case _ => None
  end extension
end nullable
