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

import java.io.File
import java.security.MessageDigest

import world.sbt.WorldPluginImports.*

import sbt.*
import sbt.Keys.*

/** Generates world's presentation code for a build that declares what it needs.
  *
  * A build declares the locales it needs; the plugin slices world's curated corpus to exactly those
  * and writes the sources that compile against the library. Generation is content-hashed against the
  * corpus, the declarations, and the generator's own version, and its output is byte-stable, so a
  * build that changes nothing recompiles nothing.
  *
  * @see
  *   [[WorldPluginImports$ WorldPluginImports]] for every setting and task.
  */
object WorldPlugin extends AutoPlugin:

  override def requires: Plugins = plugins.JvmPlugin
  override def trigger: PluginTrigger = noTrigger

  val autoImport: WorldPluginImports.type = WorldPluginImports

  override def projectConfigurations: Seq[Configuration] = Seq(WorldData)

  override def projectSettings: Seq[Setting[?]] = Seq
    (
      worldLocales := Seq("en"),
      worldPackage := "world.generated",
      worldDefaultLocale := worldLocales.value.headOption.getOrElse("en"),
      libraryDependencies += "africa.shuwari" %% "world-data" % BuildInfo.version % WorldData,
      // The generator hands sbt plain files, which the 2.x action cache cannot carry as a task result,
      // so the key opts out of it and the task does its own tracking against a content hash.
      worldCatalogue := (Compile / sourceDirectory).value / "messages" / "reference.txt",
      worldTranslations := (Compile / sourceDirectory).value / "messages",
      worldGenerate := Def.uncached(generate.value),
      worldMessages := Def.uncached(messages.value),
      Compile / sourceGenerators += worldGenerate.taskValue,
      Compile / sourceGenerators += worldMessages.taskValue
    )

  /** The corpus jars on the hidden configuration, which is how world-data reaches a build without
    * ever joining its compile or runtime classpath.
    */
  private def corpus: Def.Initialize[Task[Seq[File]]] = Def.task {
    val converter = fileConverter.value
    Classpaths
      .managedJars(WorldData, classpathTypes.value, update.value, converter)
      .map(_.data)
      .map(converter.toPath)
      .map(_.toFile)
  }

  private def generate: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val declared = worldLocales.value
    val target = (Compile / sourceManaged).value / "world"
    val jars = corpus.value
    val stamp = target / "cultures.hash"
    val written = target / "cultures.scala"
    // The action cache hashes a task's own code, not the code it calls, so the generator's version
    // enters the key explicitly: a plugin upgrade that changes emitted output must invalidate it.
    val key = digest
      (
        BuildInfo.version +: worldPackage.value +: worldDefaultLocale.value +:
          declared.toVector.sorted ++: jars.sortBy(_.getPath).map(jar => s"${jar.getName}:${jar.length.toString}")
      )
    if written.isFile && stamp.isFile && IO.read(stamp) == key then Seq(written)
    else
      val source = for
        read <- Corpus.read(jars)
        resolved <- resolve(read, declared)
        text <- Emit(worldPackage.value, resolved, worldDefaultLocale.value)
      yield text
      source match
        case Left(fault) => sys.error(s"sbt-world: ${fault.message}")
        case Right(text) =>
          IO.write(written, text)
          IO.write(stamp, key)
          log.info(s"[world] generated ${declared.size.toString} cultures into ${written.getName}")
          Seq(written)
    end if
  }

  /** The message objects, when the build declares a catalogue: a build with no catalogue generates
    * no messages rather than an empty trait.
    */
  private def messages: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val catalogue = worldCatalogue.value
    val declared = worldLocales.value
    val target = (Compile / sourceManaged).value / "world"
    val written = target / "messages.scala"
    val stamp = target / "messages.hash"
    val jars = corpus.value
    if !catalogue.isFile then Seq.empty
    else
      val translations = declared.map(tag => worldTranslations.value / s"$tag.po").filter(_.isFile)
      val key = digest
        (
          BuildInfo.version +: worldPackage.value +: worldDefaultLocale.value +: IO.read(catalogue) +:
            declared.toVector.sorted ++: translations.sortBy(_.getPath).map(one => IO.read(one))
        )
      if written.isFile && stamp.isFile && IO.read(stamp) == key then Seq(written)
      else
        val source = for
          read <- Corpus.read(jars)
          entries <- Messages.catalogue(IO.read(catalogue))
          locales <- translated(read, declared, worldTranslations.value)
          text <- Messages(worldPackage.value, entries, locales, Emit.member(worldDefaultLocale.value).capitalize)
        yield text
        source match
          case Left(fault) => sys.error(s"sbt-world: ${fault.message}")
          case Right(text) =>
            IO.write(written, text)
            IO.write(stamp, key)
            log.info(s"[world] generated messages for ${declared.size.toString} locales into ${written.getName}")
            Seq(written)
      end if
    end if
  }

  /** Each declared locale's own category set and its translations, in declaration order. */
  private def translated
      (read: Corpus, declared: Seq[String], directory: File): Either[Fault,
                                                                     Vector[(String, String, Vector[String], Map[String, Translation])]] =
    val locales = declared.toVector.map { tag =>
      val member = Emit.member(tag)
      val file = directory / s"$tag.po"
      for
        resolved <- Cultures.resolve(read, tag)
        categories = Messages.selected(rulesOf(resolved))
        entries <-
          if file.isFile then Messages.po(IO.read(file), categories.filterNot(_ == "other") :+ "other")
          else Right(Vector.empty[Translation])
      yield (member.capitalize, s"Cultures.$member", categories, entries.map(one => one.key -> one).toMap)
    }
    locales.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(locales.collect { case Right(one) => one })
  end translated

  // The categories a locale selects are visible in the selector the generator already compiled for
  // it, which is the same rule set its translations must answer.
  private def rulesOf(resolved: Resolved): Vector[(String, String)] =
    Vector("zero", "one", "two", "few", "many")
      .filter(category => resolved.cardinal.contains(s"Plural.${category.capitalize}"))
      .map(category => category -> "")

  private def resolve(read: Corpus, declared: Seq[String]): Either[Fault, Vector[Resolved]] =
    val resolved = declared.toVector.map(tag => Cultures.resolve(read, tag))
    resolved.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(resolved.collect { case Right(one) => one })

  private def digest(parts: Seq[String]): String =
    val sha = MessageDigest.getInstance("SHA-256")
    parts.foreach(part => sha.update(part.getBytes("UTF-8")))
    sha.digest().map(byte => f"$byte%02x").mkString
end WorldPlugin
