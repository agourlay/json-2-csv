package test

import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ConverterSpec extends WordSpec with Matchers {

  "The converter" must {
    "transform properly the nominal case" in {
      val inputFile = new File(getClass.getResource("/test.json").getPath())
      val resultFile = Converter.fileConversion(inputFile)
      val resultFileContent = FileUtils.readFileToString(resultFile)
      FileUtils.forceDelete(resultFile)

      val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
      val referenceResult = FileUtils.readFileToString(referenceResultFile)
      resultFileContent shouldEqual referenceResult
    }
  }
}