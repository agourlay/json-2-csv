package test

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
}