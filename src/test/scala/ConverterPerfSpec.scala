package test

import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils

import json2CsvStream._

class ConverterPerfSpec extends WordSpec with Matchers {

  // test heap size options in build.sbt
  "The converter within 2Go heap" must {

    "convert stream of 30.000 elements" in {
      stressMemoryTestBuilder(10000)
    }

    "convert stream of 300.000 elements" in {
      stressMemoryTestBuilder(100000)
    }

    "convert stream of 3.000.000 elements" in {
      stressMemoryTestBuilder(1000000)
    }
  }

  // Helper to stressTest memory. 
  // Not blowing up here means success.
  def stressMemoryTestBuilder(n: Int) {
    val resultFileName = "shouldBeDeleted.csv"
    val inputStream = repeatTestFileContent(n)
    Converter.streamConversion(inputStream, resultFileName)
    FileUtils.forceDelete(new File(resultFileName))
    assert(true)
  }

  // Helper to build Stream[String] from the test.json containing 3 objects.
  def repeatTestFileContent(n: Int): Stream[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath())
    // remove "[]"
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1)
    "[" #:: Stream.continually[String](twoJsons + ",").take(n - 1) :+ twoJsons :+ ("]")
  }
}