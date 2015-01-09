package com.github.agourlay.test

import org.scalatest.{ WordSpec, Matchers }
import java.io.{ File, FileOutputStream }
import org.apache.commons.io.FileUtils

import com.github.agourlay.json2CsvStream._

class ConverterAcceptanceSpec extends WordSpec with Matchers {

  "The converter" must {
    "transform properly the nominal case" in {
      val inputFile = new File(getClass.getResource("/test.json").getPath())
      val outputName = "delete.csv"
      val resultOutputStream = new FileOutputStream(outputName)
      Json2CsvStream.convert(inputFile, resultOutputStream)

      val resultFile = new File(outputName)
      val resultFileContent = FileUtils.readFileToString(resultFile)
      FileUtils.forceDelete(resultFile)

      val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath())
      resultFileContent shouldEqual FileUtils.readFileToString(referenceResultFile)
    }
  }
}