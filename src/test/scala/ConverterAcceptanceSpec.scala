package test

import org.scalatest._
import java.io._
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ConverterAcceptanceSpec extends WordSpec with Matchers {

  "The converter" must {
    "transform properly the nominal case" in {
      val inputFile = new File(getClass.getResource("/test.json").getPath())
      val outputName = "delete.csv"
      val resultOutputStream = new FileOutputStream(outputName)
      Converter.fileConversion(inputFile, resultOutputStream)

      val resultFile = new File(outputName)
      val resultFileContent = FileUtils.readFileToString(resultFile)
      FileUtils.forceDelete(resultFile)

      val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
      resultFileContent shouldEqual FileUtils.readFileToString(referenceResultFile)
    }
  }
}