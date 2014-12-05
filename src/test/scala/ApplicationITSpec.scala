package test

import scala.annotation.tailrec
import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ApplicationITSpec extends FlatSpec with Matchers {

  "The application" should "work with nominal case" in {
    val resultFileName = Json2CsvStream.process(getClass.getResource("/test.json").getPath())
    val resultFile = new File(resultFileName)
    val resultFileContent = FileUtils.readFileToString(resultFile)
    FileUtils.forceDelete(resultFile)

    val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
    val referenceResult = FileUtils.readFileToString(referenceResultFile)
    resultFileContent shouldEqual referenceResult
  }

  def repeatTestFileContent(n: Int): Stream[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath())
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1) // remove "[]"
    "[" #:: Stream.continually[String](twoJsons + ",").take(n) :+ ("]") // fix trailing comma on last element
  }

  "The application" should "not blow up the heap while transforming huge stream" in {
    val resultFileName = "shouldBeDeleted.csv"
    val inputStream = repeatTestFileContent(10000) // decreasing size while looking for fix
    Json2CsvStream.streamConversion(inputStream, resultFileName)
    FileUtils.forceDelete(new File(resultFileName))
  }

}