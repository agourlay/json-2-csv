package com.github.agourlay

import java.io.{ File, FileOutputStream }
import java.nio.charset.Charset

import com.github.agourlay.json2Csv._
import org.apache.commons.io.FileUtils

class ConverterAcceptanceSpec extends munit.FunSuite {

  test("transform properly the nominal case") {
    val inputFile = new File(getClass.getResource("/test.json").getPath)
    val outputName = "delete.csv"
    val resultOutputStream = new FileOutputStream(outputName)
    Json2Csv.convert(inputFile, resultOutputStream)

    val resultFile = new File(outputName)
    val resultFileContent = FileUtils.readFileToString(resultFile, Charset.defaultCharset)
    FileUtils.forceDelete(resultFile)

    val referenceResultFile = new File(getClass.getResource("/test-json.csv").getPath)
    val referenceResultContent = FileUtils.readFileToString(referenceResultFile, Charset.defaultCharset)
    assertEquals(resultFileContent, referenceResultContent)
  }

}