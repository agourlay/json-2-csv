package test

import scala.annotation.tailrec
import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ApplicationITSpec extends FlatSpec with Matchers {

  "The converter" should "work with nominal case" in {
    val inputFile = new File(getClass.getResource("/test.json").getPath())
    val resultFile = Converter.fileConversion(inputFile)
    val resultFileContent = FileUtils.readFileToString(resultFile)
    FileUtils.forceDelete(resultFile)

    val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
    val referenceResult = FileUtils.readFileToString(referenceResultFile)
    resultFileContent shouldEqual referenceResult
  }

  def repeatTestFileContent(n: Int): Stream[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath())
    // remove "[]"
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1)
    "[" #:: Stream.continually[String](twoJsons + ",").take(n - 1) :+ twoJsons :+ ("]")
  }

  "The converter" should "not blow up the heap while transforming huge stream" in {
    val resultFileName = "shouldBeDeleted.csv"
    val inputStream = repeatTestFileContent(20000) // decreasing size while looking for fix
    Converter.streamConversion(inputStream, resultFileName)
    FileUtils.forceDelete(new File(resultFileName))
  }
}