package com.github.agourlay

import java.io.File
import java.nio.charset.Charset

import com.github.agourlay.json2Csv._
import org.apache.commons.io.FileUtils
import org.apache.commons.io.output.NullOutputStream

class ConverterPerfSpec extends munit.FunSuite {

  // test heap size options in build.sbt
  test("convert a stream of 3.000.000 JSON elements within 1GB") {
    stressMemoryTestBuilder(3000000) match {
      case Left(e) =>
        fail("failed with", e)
      case Right(count) =>
        assertEquals(count, 4999995L)
    }
  }

  // Helper to stressTest memory.
  def stressMemoryTestBuilder(n: Int): Either[Exception, Long] = {
    // test.json containing 3 objects.
    Json2Csv.convert(repeatTestFileContent(n / 3), NullOutputStream.NULL_OUTPUT_STREAM)
  }

  // Helper to build Stream[String] from the test.json containing 3 objects.
  def repeatTestFileContent(n: Int): LazyList[String] = {
    val resultFile = new File(getClass.getResource("/test.json").getPath)
    // remove "[]"
    val twoJsons = FileUtils.readFileToString(resultFile, Charset.defaultCharset).drop(1).dropRight(1)
    val elem = twoJsons + ","
    "[" #:: LazyList.fill(n - 1)(elem)
    // The stream does not end properly with json,json] but the goal here is to test memory and not the parsing itself.
    // For proper ending add :+ twoJsons :+ ("]") but it adds memoization to the Stream.
  }
}