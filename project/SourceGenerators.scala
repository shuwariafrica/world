import sbt.*
import sbt.Keys.*

/** Defines the sbt tasks for automatically generating source files from data.
  *
  * This object encapsulates the logic for caching and executing the code
  * generators, ensuring they only run when input data files have changed.
  */
object SourceGenerators {

  // Defines the task for the 'locale' module.
  val countriesGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value

    val inputFiles: Set[File] = Set(
      projectRootDir / "countries-iso3166.csv",
      projectRootDir / "supplemental-countries.yml"
    )

    val generate: Set[File] => Set[File] = { changedFiles =>
      log.info(s"SourceGenerators: ${changedFiles.size} country data file(s) changed. Regenerating Countries.scala...")
      val outputFile = sourceManagedDir / "africa" / "shuwari" / "locale" / "country" / "Countries.scala"
      val (generatedContent, generatedFile) = CountriesPopulator.generate(projectRootDir, outputFile, log)
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

  // Defines the task for the 'money' module (Currencies + FactorySyntax).
  val currenciesGeneratorTask: Def.Initialize[Task[Seq[File]]] = Def.task {
    val log = streams.value.log
    val sourceManagedDir = (Compile / sourceManaged).value
    val projectRootDir = (ThisBuild / baseDirectory).value

    val inputFiles: Set[File] = Set(
      projectRootDir / "currencies.yml"
    )

    val generate: Set[File] => Set[File] = { changedFiles =>
      log.info(s"SourceGenerators: ${changedFiles.size} currency data file(s) changed. Regenerating currency sources...")
      val outputDir = sourceManagedDir / "africa" / "shuwari"
      val generated: Map[File, String] = CurrenciesPopulator.generateCurrencies(projectRootDir, outputDir, log)
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

    val inputFiles: Set[File] = Set(
      projectRootDir / "countries-iso3166.csv",
      projectRootDir / "supplemental-countries.yml",
      projectRootDir / "currencies.yml",
      projectRootDir / "currency-usage.yml"
    )

    val generate: Set[File] => Set[File] = { changedFiles =>
      log.info(s"SourceGenerators: ${changedFiles.size} usage data file(s) changed. Regenerating CurrencyUsageInstances...")
      val outputDir = sourceManagedDir / "africa" / "shuwari"
      val generated: Map[File, String] = CurrenciesPopulator.generateUsage(projectRootDir, outputDir, log)
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
