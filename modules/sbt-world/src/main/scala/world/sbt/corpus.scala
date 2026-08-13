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
import java.util.zip.ZipFile

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

// One curated dataset, as rows of already-split cells keyed by the column names its header
// declares.
final private[sbt] case class Table(columns: Vector[String], rows: Vector[Vector[String]]):

  // The cell at `column`, or the empty string where the row declares nothing there. A dataset that
  // does not carry the column at all is a corpus fault rather than an empty cell, so a renamed
  // column can never read as an absent value.
  def at(row: Vector[String], column: String): Either[Fault, String] =
    columns.indexOf(column) match
      case -1 => Left(Fault.Corpus(s"carries no '$column' column"))
      case at => Right(row.lift(at).getOrElse(""))

  def keyed(column: String): Either[Fault, Map[String, Vector[String]]] =
    columns.indexOf(column) match
      case -1 => Left(Fault.Corpus(s"carries no '$column' column"))
      case at => Right(rows.map(row => row.lift(at).getOrElse("") -> row).toMap)
end Table

// World's curated presentation corpus, as the generator reads it: the tables themselves, and the
// locale inheritance the CLDR data is stored against.
//
// Every value in the corpus is what its own locale DECLARES. Resolution walks the inheritance
// chain, which is why the curated parent-locale rows exist: CLDR's chain is not truncation alone.
final private[sbt] case class Corpus
    (
      cultures: Table,
      names: Table,
      currencies: Table,
      plurals: Table,
      calendars: Table,
      numbering: Table,
      systems: Table,
      territories: Table,
      languageRegister: Table,
      scriptRegister: Table,
      currencyRegister: Table,
      parents: Map[String, String],
      likely: Map[String, (String, String)],
      directions: Map[String, String]
    ):

  // The locale's parent, by CLDR's own rule: a curated parent-locale row where one exists, then
  // truncation of the last subtag, and root beneath everything.
  def parent(tag: String): String =
    parents.getOrElse
      (
        tag,
        tag.lastIndexOf('-') match
          case -1 => if tag == "root" then "" else "root"
          case at => tag.substring(0, at)
      )

  // The inheritance chain from a locale up to root, the order every field lookup reads in.
  def chain(tag: String): Vector[String] =
    @tailrec def walk(at: String, seen: Vector[String]): Vector[String] =
      if at.isEmpty || seen.contains(at) then seen else walk(parent(at), seen :+ at)
    walk(tag, Vector.empty)
end Corpus

// Reads the curated corpus out of the world-data artefact.
private[sbt] object Corpus:

  private val prefix = "world/data/"

  private def parse(name: String, text: String): Either[Fault, Table] =
    val lines = text.linesIterator.toVector
    lines.find(_.startsWith("# columns: ")) match
      case None         => Left(Fault.Corpus(s"dataset '$name' declares no columns in its header"))
      case Some(header) =>
        val columns = header.drop("# columns: ".length).split(",").toVector.map(_.trim)
        val rows = lines.filterNot(line => line.startsWith("#") || line.isEmpty).map(_.split("\t", -1).toVector)
        rows.find(_.sizeIs > columns.size) match
          case Some(row) =>
            Left(Fault.Corpus(s"dataset '$name' carries a row of ${row.size.toString} cells against ${columns.size.toString} columns"))
          case None => Right(Table(columns, rows))

  private def entries(archives: Seq[File]): Either[Fault, Map[String, String]] =
    val found = archives.flatMap { archive =>
      val zip = ZipFile(archive)
      try
        zip
          .entries()
          .asScala
          .toVector
          .filter(entry => entry.getName.startsWith(prefix) && entry.getName.endsWith(".tsv"))
          .map { entry =>
            val name = entry.getName.drop(prefix.length).dropRight(".tsv".length)
            name -> String(zip.getInputStream(entry).readAllBytes(), "UTF-8")
          }
      finally zip.close()
    }
    if found.isEmpty then Left(Fault.Corpus("is absent from the build's classpath")) else Right(found.toMap)
  end entries

  private def loose(directory: File): Either[Fault, Map[String, String]] =
    Option(directory.listFiles((_, name) => name.endsWith(".tsv"))).map(_.toVector) match
      case None | Some(Vector()) => Left(Fault.Corpus(s"carries no datasets under ${directory.getPath}"))
      case Some(found)           =>
        Right
          (found.map(file => file.getName.dropRight(".tsv".length) -> String(java.nio.file.Files.readAllBytes(file.toPath), "UTF-8")).toMap)

  private def table(files: Map[String, String], name: String): Either[Fault, Table] =
    files.get(name) match
      case None       => Left(Fault.Corpus(s"carries no '$name' dataset"))
      case Some(text) => parse(name, text)

  private def pairs(table: Table, key: String, value: String): Either[Fault, Map[String, String]] =
    val read = table.rows.map
      (row =>
        for
          left <- table.at(row, key)
          right <- table.at(row, value)
        yield left -> right)
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(pair) => pair }.toMap)

  // Reads every dataset the culture pipeline consumes out of the world-data jars on the build's own
  // hidden configuration.
  def read(archives: Seq[File]): Either[Fault, Corpus] = entries(archives).flatMap(assemble)

  // Reads the same datasets from a directory of loose files, which is how world's own build reads
  // the corpus it curates before any of it is packaged.
  def read(directory: File): Either[Fault, Corpus] = loose(directory).flatMap(assemble)

  private def assemble(files: Map[String, String]): Either[Fault, Corpus] =
    for
      cultures <- table(files, "cultures")
      names <- table(files, "culture-names")
      currencies <- table(files, "culture-currencies")
      plurals <- table(files, "plural-rules")
      calendars <- table(files, "calendar-preferences")
      numbering <- table(files, "numbering-systems")
      systems <- table(files, "culture-numbering")
      territories <- table(files, "territories")
      languageRegister <- table(files, "languages")
      scriptRegister <- table(files, "scripts")
      currencyRegister <- table(files, "currencies")
      parentTable <- table(files, "parent-locales")
      likelyTable <- table(files, "likely-subtags")
      scriptTable <- table(files, "scripts")
      parents <- pairs(parentTable, "locale", "parent")
      directions <- pairs(scriptTable, "code", "direction")
      likely <- likelySubtags(likelyTable)
    yield Corpus
      (
        cultures,
        names,
        currencies,
        plurals,
        calendars,
        numbering,
        systems,
        territories,
        languageRegister,
        scriptRegister,
        currencyRegister,
        parents,
        likely,
        directions
      )

  private def likelySubtags(table: Table): Either[Fault, Map[String, (String, String)]] =
    val read = table.rows.map
      (row =>
        for
          language <- table.at(row, "language")
          script <- table.at(row, "script")
          region <- table.at(row, "region")
        yield language -> (script, region))
    read.collectFirst { case Left(fault) => fault } match
      case Some(fault) => Left(fault)
      case None        => Right(read.collect { case Right(pair) => pair }.toMap)
end Corpus
