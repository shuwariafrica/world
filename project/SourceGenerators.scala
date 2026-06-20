import sbt.*
import sbt.Keys.*

/** Defines the sbt tasks for generating source files from CLDR data.
  *
  * All generators read from the CLDR git submodule at `data/cldr`.
  * The submodule must be initialised before compilation.
  */
object SourceGenerators {

  /** Path to the CLDR data submodule. */
  private def cldrDir(projectRoot: File): File = projectRoot / "data" / "cldr"

  /** Verifies the CLDR submodule is initialised. Fails the build with a clear message if not. */
  private def requireCldr(projectRoot: File): File = {
    val cldr = cldrDir(projectRoot)
    val marker = cldr / "common" / "supplemental" / "supplementalData.xml"
    if (!marker.exists())
      sys.error(s"""CLDR data submodule not initialised at ${cldr.getAbsolutePath}.
                   |Run: git submodule update --init --depth 1
                   |""".stripMargin)
    cldr
  }

  /** CLDR input files used by the countries generator. */
  private def countryInputFiles(cldr: File): Set[File] = Set(
    cldr / "common" / "validity" / "region.xml",
    cldr / "common" / "supplemental" / "supplementalData.xml",
    cldr / "common" / "main" / "en.xml"
  )

  /** CLDR input files used by the currencies generator. */
  private def currencyInputFiles(cldr: File): Set[File] = Set(
    cldr / "common" / "validity" / "currency.xml",
    cldr / "common" / "supplemental" / "supplementalData.xml",
    cldr / "common" / "main" / "en.xml"
  )

  // Defines the task for the 'locale' module.
  val countriesGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles = countryInputFiles(cldr)

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating Countries.scala from CLDR...")
      val outputDir = sourceManagedDir / "world" / "locale" / "country"
      val (generatedContent, generatedFile) = CountriesPopulator.generate(cldr, outputDir, log)
      IO.write(generatedFile, generatedContent)
      log.info(s"SourceGenerators: Finished generating ${generatedFile.getName}.")
      Set(generatedFile)
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-countries-generator",
      inStyle = FileInfo.lastModified,
      outStyle = FileInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }

  // Defines the task for the 'locale' module - Languages.
  val languagesGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles: Set[File] = Set(
      cldr / "common" / "supplemental" / "supplementalData.xml",
      cldr / "common" / "main" / "en.xml"
    )

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating Languages.scala from CLDR...")
      val outputDir = sourceManagedDir / "world" / "locale" / "language"
      val (generatedContent, generatedFile) = LanguagesGenerator.generate(cldr, outputDir, log)
      IO.write(generatedFile, generatedContent)
      log.info(s"SourceGenerators: Finished generating ${generatedFile.getName}.")
      Set(generatedFile)
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-languages-generator",
      inStyle = FileInfo.lastModified,
      outStyle = FileInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }

  // Defines the task for the 'locale' module - Scripts.
  val scriptsGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles: Set[File] = Set(
      cldr / "common" / "validity" / "script.xml",
      cldr / "common" / "main" / "en.xml"
    )

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating Scripts.scala from CLDR...")
      val outputDir = sourceManagedDir / "world" / "locale" / "script"
      val (generatedContent, generatedFile) = ScriptsGenerator.generate(cldr, outputDir, log)
      IO.write(generatedFile, generatedContent)
      log.info(s"SourceGenerators: Finished generating ${generatedFile.getName}.")
      Set(generatedFile)
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-scripts-generator",
      inStyle = FileInfo.lastModified,
      outStyle = FileInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }

  // Defines the task for the 'locale' module - LikelySubtags.
  val likelySubtagsGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles: Set[File] = Set(
      cldr / "common" / "supplemental" / "likelySubtags.xml"
    )

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating LikelySubtags.scala from CLDR...")
      val outputDir = sourceManagedDir / "world" / "locale"
      val (generatedContent, generatedFile) = LikelySubtagsGenerator.generate(cldr, outputDir, log)
      IO.write(generatedFile, generatedContent)
      log.info(s"SourceGenerators: Finished generating ${generatedFile.getName}.")
      Set(generatedFile)
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-likely-subtags-generator",
      inStyle = FileInfo.lastModified,
      outStyle = FileInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }

  // Defines the task for the 'money' module (Currencies + FactorySyntax).
  val currenciesGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles = currencyInputFiles(cldr)

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating currency sources from CLDR...")
      val outputDir = sourceManagedDir / "world" / "money"
      val generated: Map[File, String] = CurrenciesPopulator.generateCurrencies(cldr, outputDir, log)
      generated.foreach { case (file, content) => IO.write(file, content) }
      log.info(s"SourceGenerators: Finished generating ${generated.size} currency source file(s).")
      generated.keySet
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-currencies-generator",
      inStyle = FilesInfo.lastModified,
      outStyle = FilesInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }

  // Defines the task for the 'money-usage' module (CurrencyUsageInstances).
  val currencyUsageGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value
    val cldr = requireCldr(projectRootDir)

    val inputFiles = countryInputFiles(cldr) ++ currencyInputFiles(cldr)

    val generate: Set[File] => Set[File] = { _ =>
      log.info("SourceGenerators: Regenerating CurrencyUsageInstances from CLDR...")
      val outputDir = sourceManagedDir / "world" / "money"
      val generated: Map[File, String] = CurrenciesPopulator.generateUsage(cldr, outputDir, log)
      generated.foreach { case (file, content) => IO.write(file, content) }
      log.info(s"SourceGenerators: Finished generating ${generated.size} usage source file(s).")
      generated.keySet
    }

    val cachedGenerate = FileFunction.cached(
      streams.value.cacheDirectory / "sbt-currency-usage-generator",
      inStyle = FilesInfo.lastModified,
      outStyle = FilesInfo.exists
    )(generate)

    cachedGenerate(inputFiles).toSeq
  }
}
