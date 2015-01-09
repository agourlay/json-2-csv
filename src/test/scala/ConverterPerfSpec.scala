package com.github.agourlay.test

import org.scalatest.{ WordSpec, Matchers }
import java.io.File
import org.apache.commons.io.FileUtils
import org.apache.commons.io.output.NullOutputStream

import com.github.agourlay.json2CsvStream._

class ConverterPerfSpec extends WordSpec with Matchers {

  // test heap size options in build.sbt
  "The converter within 1 Gb heap" must {
    "convert a stream of 3.000.000 JSON elements" in {
      stressMemoryTestBuilder(3000000)
    }
  }

  // Helper to stressTest memory. 
  def stressMemoryTestBuilder(n: Int) {
    // test.json containing 3 objects.
    Json2CsvStream.convert(repeatTestFileContent(n / 3), new NullOutputStream())
    assert(true) // Not blowing up here means success.
  }

  // Helper to build Stream[String] from the test.json containing 3 objects.
  def repeatTestFileContent(n: Int): Stream[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath())
    // remove "[]"
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1)
    "[" #:: Stream.continually[String](twoJsons + ",").take(n - 1)
    // The stream does not end properly with json,json] but the goal here is to test memory and not the parsing itself.
    // For proper ending add :+ twoJsons :+ ("]") but it adds memoization to the Stream. 
  }
}