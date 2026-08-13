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
package world.sbt

import sbt.Configuration
import sbt.SettingKey
import sbt.TaskKey
import sbt.librarymanagement.Configurations
import sbt.settingKey
import sbt.taskKey

/** The settings and tasks a build enables with `WorldPlugin`.
  *
  * All members are automatically available in a `build.sbt` that enables the plugin.
  */
object WorldPluginImports:

  /** The configuration world's curated data arrives on: a build-time corpus of several megabytes
    * that must never reach a consumer's own compile or runtime classpath, so it is hidden and the
    * generator resolves it by name.
    */
  val WorldData: Configuration = Configurations.config("world-data").hide

  /** The locales this build generates cultures for, as language tags.
    *
    * A tag may select a declared alternate numbering system through the `u-nu` extension
    * (`"ar-EG-u-nu-latn"`); a private-use tag is refused, because no dataset can source one - compose
    * that bundle by hand through `Culture(locale, data)` instead.
    */
  val worldLocales: SettingKey[Seq[String]] =
    settingKey("Language tags to generate cultures for.")

  /** The package the generated sources are written into. */
  val worldPackage: SettingKey[String] =
    settingKey("Package for the generated cultures and messages.")

  /** The locale whose culture an unmatched negotiation lands on. Defaults to the first declared. */
  val worldDefaultLocale: SettingKey[String] =
    settingKey("Language tag whose culture serves as the negotiation default.")

  /** The reference catalogue of messages this build generates typed methods for. */
  val worldCatalogue: SettingKey[java.io.File] =
    settingKey("Reference message catalogue.")

  /** The directory holding the translators' PO files, one per locale, named by language tag. */
  val worldTranslations: SettingKey[java.io.File] =
    settingKey("Directory of translated PO files, one per declared locale.")

  /** Generates the message trait and its per-locale objects, returning the files written. */
  val worldMessages: TaskKey[Seq[java.io.File]] =
    taskKey("Generate the message objects from the reference catalogue and its translations.")

  /** Generates the declared cultures, returning the files written. */
  val worldGenerate: TaskKey[Seq[java.io.File]] =
    taskKey("Generate the declared cultures from world's curated corpus.")
end WorldPluginImports
