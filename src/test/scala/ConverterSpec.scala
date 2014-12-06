package test

import scala.annotation.tailrec
import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ConverterSpec extends WordSpec with Matchers {

  "The converter within 2Go heap" must {
    "work with nominal case" in {
      val inputFile = new File(getClass.getResource("/test.json").getPath())
      val resultFile = Converter.fileConversion(inputFile)
      val resultFileContent = FileUtils.readFileToString(resultFile)
      FileUtils.forceDelete(resultFile)

      val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
      val referenceResult = FileUtils.readFileToString(referenceResultFile)
      resultFileContent shouldEqual referenceResult
    }

    "convert stream of 3.000 elements" in {
      val resultFileName = "shouldBeDeleted.csv"
      val inputStream = repeatTestFileContent(1000) // decreasing size while looking for fix
      Converter.streamConversion(inputStream, resultFileName)
      FileUtils.forceDelete(new File(resultFileName))
    }

    "convert stream of 30.000 elements" in {
      val resultFileName = "shouldBeDeleted.csv"
      val inputStream = repeatTestFileContent(10000) // decreasing size while looking for fix
      Converter.streamConversion(inputStream, resultFileName)
      FileUtils.forceDelete(new File(resultFileName))
    }

    "convert stream of 300.000 elements" in {
      val resultFileName = "shouldBeDeleted.csv"
      val inputStream = repeatTestFileContent(100000) // decreasing size while looking for fix
      Converter.streamConversion(inputStream, resultFileName)
      FileUtils.forceDelete(new File(resultFileName))
    }

    "convert stream of 3.000.000 elements" in {
      val resultFileName = "shouldBeDeleted.csv"
      val inputStream = repeatTestFileContent(1000000) // decreasing size while looking for fix
      Converter.streamConversion(inputStream, resultFileName)
      FileUtils.forceDelete(new File(resultFileName))
    }
  }

  // helper to build Stream[String] from the test.json containing 3 objects.
  def repeatTestFileContent(n: Int): Stream[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath())
    // remove "[]"
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1)
    "[" #:: Stream.continually[String](twoJsons + ",").take(n - 1) :+ twoJsons :+ ("]")
  }
}