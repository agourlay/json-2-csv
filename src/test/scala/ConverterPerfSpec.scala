package test

import org.scalatest._
import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.commons.io.output.NullOutputStream

import json2CsvStream._

class ConverterPerfSpec extends WordSpec with Matchers {

  // test heap size options in build.sbt
  "The converter within 2 Gb heap" must {

    "convert stream of 10.000 elements" in {
      stressMemoryTestBuilder(10000)
    }

    "convert stream of 100.000 elements" in {
      stressMemoryTestBuilder(100000)
    }

    "convert stream of 1.000.000 elements" in {
      stressMemoryTestBuilder(1000000)
    }

    "convert stream of 10.000.000 elements" in {
      stressMemoryTestBuilder(10000000)
    }
  }

  // Helper to stressTest memory. 
  // Not blowing up here means success.
  def stressMemoryTestBuilder(n: Int) {
    // test.json containing 3 objects.
    val inputStream = repeatTestFileContent(n / 3)
    Converter.streamConversion(inputStream, new NullOutputStream())
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