package test

import scala.annotation.tailrec
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

  // TODO fix stackoverflow in Stream gen.
  def repeatTestFileContent(n: Int): Stream[String] = {
    @tailrec
    def loopString(v: Int, s: String, acc: Stream[String]): Stream[String] = {
      if (v == 0) acc
      else {
        val input = if (acc.isEmpty) acc :+ (",") else acc
        loopString(v - 1, s, input :+ (s))
      }
    }

    val resultFile = new File(getClass.getResource("/test.json").getPath())
    val twoJsons = FileUtils.readFileToString(resultFile).drop(1).dropRight(1) // remove "[]"
    val acc = loopString(n, twoJsons, Stream())
    val stream: Stream[String] = "[" #:: acc :+ ("]")
    stream
  }

  "The application" should "not blow up the heap while transforming huge stream" in {
    val resultFileName = "shouldBeDeleted.csv"
    val inputStream = repeatTestFileContent(2000)
    Json2CsvStream.streamConversion(inputStream, resultFileName)
    FileUtils.forceDelete(new File(resultFileName))
  }

}