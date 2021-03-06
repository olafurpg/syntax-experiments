package experiment

import java.util
import java.util.Comparator
import java.util.concurrent.atomic.AtomicInteger
import scala.meta._
import org.langmeta.internal.semanticdb.schema
import scala.meta.testkit.SyntaxAnalysis
import scala.util.Sorting

object DefMacroUsage {
  sealed trait Config
  object Config {
    case object Test extends Config
    case object Main extends Config
    case object Unknown extends Config
  }
  case class MacroCall(symbol: String, config: Config)
  def apply(): String = {
    val results = SemanticAnalysis.run { ctx =>
      def allNames(
          document: schema.Document): Traversable[schema.ResolvedName] =
        new Traversable[schema.ResolvedName] {
          override def foreach[U](f: schema.ResolvedName => U): Unit = {
            document.names.foreach(f)
            document.synthetics.foreach(_.names.foreach(f))
          }
        }
      for {
        document <- ctx.sdatabase.documents
        config = {
          if (document.filename.contains("/test/")) Config.Test
          else Config.Main
        }
        name <- allNames(document)
        if !name.isDefinition
        denot <- ctx.denotation(name.symbol)
        if denot.isMacro
      } yield MacroCall(name.symbol, config)
    }

    val sb = new StringBuilder
    def println(s: String) = {
      sb ++= s
      sb ++= "\n"
    }

    results
      .flatMap(_._2)
      .groupBy(_.config)
      .foreach {
        case (config, calls) =>
          println(s"# $config")
          println("```")
          calls
            .groupBy(_.symbol)
            .mapValues(_.size)
            .toSeq
            .sortBy(-_._2)
            .foreach {
              case (symbol, count) =>
                if (count > 5) {
                  println(f"$count%10s: $symbol")
                }
            }
          println("```")
      }
    sb.toString()
  }
}
