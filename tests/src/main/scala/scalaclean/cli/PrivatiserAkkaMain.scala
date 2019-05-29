package scalaclean.cli

import java.nio.charset.StandardCharsets

import scalaclean.cli.FileHelper.toPlatform
import scalaclean.rules.AbstractRule
import scalaclean.rules.privatiser.Privatiser
import scalafix.internal.patch.PatchInternals
import scalafix.internal.reflect.ClasspathOps
import scalafix.lint.RuleDiagnostic
import scalafix.rule.RuleName
import scalafix.scalaclean.cli.DocHelper
import scalafix.testkit.DiffAssertions
import scalafix.v1.SemanticDocument

import scala.meta._
import scala.meta.internal.io.FileIO

object PrivatiserAkkaMain {
  def main(args: Array[String]): Unit = {
    new PrivatiserAkkaMain().run
  }
}

class PrivatiserAkkaMain extends DiffAssertions {

  val projectName = "akka-actor"
  val akkaWorkspace = "/workspace/akka"
  val ivyDir = toPlatform("$HOME$/.ivy2/cache")
  val storagePath = toPlatform("$HOME$/Downloads/temp3")

  val targetFiles = List(
    RelativePath("akka/actor/Timers.scala"),
  )

// /workspace/akka/akka-actor/target/classes/META-INF/semanticdb/akka-actor/src/main/scala/akka/actor/Timers.scala.semanticdb
// /workspace/akka/akka-actor/target/classes/META-INF/semanticdb/akka-actor/src/main/scala/akka/actor/Timers.scala.semanticdb
  val outputClassDir: String = s"/workspace/akka/akka-actor/target/classes/"
  val inputClasspath = Classpath(s"$outputClassDir:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/rt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/sunrsasign.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/jre/classes:/Users/rorygraves/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.12.8.jar:/workspace/akka/akka-actor/target/classes:/Users/rorygraves/.ivy2/cache/com.typesafe/config/bundles/config-1.3.3.jar:/Users/rorygraves/.ivy2/cache/org.scala-lang.modules/scala-java8-compat_2.12/bundles/scala-java8-compat_2.12-0.8.0.jar")
  val sourceRoot = AbsolutePath(akkaWorkspace)
  val inputSourceDirectories: List[AbsolutePath] = Classpath(s"$akkaWorkspace/akka-actor/src/main/scala").entries

  def semanticPatch(
    rule: AbstractRule,
    sdoc: SemanticDocument,
    suppress: Boolean
  ): (String, List[RuleDiagnostic]) = {
    val fixes = Some(RuleName(rule.name) -> rule.fix(sdoc)).map(Map.empty + _).getOrElse(Map.empty)
    PatchInternals.semantic(fixes, sdoc, suppress)
  }

  def run: Unit = {

    val sd = inputSourceDirectories.head
    val targetFiles = FileIO.listAllFilesRecursively(sd).filter(f => f.isFile  && f.toFile.getAbsolutePath.endsWith(".scala")).map(_.toRelative(sd)).toList
    AnalysisHelper.runAnalysis(projectName, inputClasspath, sourceRoot,  inputSourceDirectories, outputClassDir, storagePath, targetFiles)
    runPrivatiser(targetFiles)
  }

  def runPrivatiser(targetFiles: Seq[RelativePath]): Unit = {

    val symtab = ClasspathOps.newSymbolTable(inputClasspath)
    val classLoader = ClasspathOps.toClassLoader(inputClasspath)

    println("---------------------------------------------------------------------------------------------------")
    // run privatiser
    val privatiser = new Privatiser()
    privatiser.beforeStart()
    targetFiles.foreach { targetFile =>
      val sdoc = DocHelper.readSemanticDoc(classLoader, symtab, inputSourceDirectories.head, sourceRoot, targetFile)
      val (fixed, messages) = semanticPatch(privatiser, sdoc, suppress = false)

      // compare results
      val tokens = fixed.tokenize.get
      val obtained = tokens.mkString

      val targetOutput = RelativePath(targetFile.toString() + ".expected")
      val outputFile = inputSourceDirectories.head.resolve(targetOutput)
      val expected = FileIO.slurp(outputFile, StandardCharsets.UTF_8)

      val diff = DiffAssertions.compareContents(obtained, expected)
      if (diff.nonEmpty) {
        println("###########> obtained       <###########")
        println(obtained)
        println("###########> expected       <###########")
        println(expected)
        println("###########> Diff       <###########")
        println(error2message(obtained, expected))

        System.out.flush()
        System.exit(1)
      }
    }
  }

}