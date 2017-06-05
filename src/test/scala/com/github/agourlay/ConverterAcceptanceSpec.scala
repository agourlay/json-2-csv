package com.github.agourlay

import java.io.{ File, FileOutputStream }
import java.nio.charset.Charset

import com.github.agourlay.json2Csv._
import org.apache.commons.io.FileUtils
import org.scalatest.{ Matchers, WordSpec }

class ConverterAcceptanceSpec extends WordSpec with Matchers {

  "The converter" must {
    "transform properly the nominal case" in {
      val inputFile = new File(getClass.getResource("/test.json").getPath)
      val outputName = "delete.csv"
      val resultOutputStream = new FileOutputStream(outputName)
      Json2Csv.convert(inputFile, resultOutputStream)

      val resultFile = new File(outputName)
      val resultFileContent = FileUtils.readFileToString(resultFile, Charset.defaultCharset)
      FileUtils.forceDelete(resultFile)

      val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath)
      resultFileContent shouldEqual FileUtils.readFileToString(referenceResultFile, Charset.defaultCharset)
    }
  }
}