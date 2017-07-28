package com.github.agourlay.json2Csv

import java.io.{ File, FileNotFoundException, OutputStream }

import com.github.tototoshi.csv.{ CSVFormat, CSVWriter, QUOTE_NONE, Quoting }
import jawn.AsyncParser
import jawn.ast.JParser._
import jawn.ast.JValue

import scala.io.Source
import scala.util.{ Failure, Try }

object Json2Csv {

  def convert(file: File, resultOutputStream: OutputStream): Try[Long] =
    if (file.isFile) convert(Source.fromFile(file, "UTF-8").getLines().toStream, resultOutputStream)
    else Failure(new FileNotFoundException("The file " + file.getCanonicalPath + " does not exists"))

  def convert(chunks: ⇒ Stream[String], resultOutputStream: OutputStream): Try[Long] = {
    val csvWriter = CSVWriter.open(resultOutputStream)(jsonCSVFormat)
    val parser = jawn.Parser.async[JValue](mode = AsyncParser.UnwrapArray)
    val finalProgress = Converter.consume(chunks, parser, csvWriter)
    csvWriter.close()
    finalProgress.map(_.rowCount)
  }

  private val jsonCSVFormat = new CSVFormat {
    val delimiter: Char = ','
    val quoteChar: Char = '"'
    val escapeChar: Char = '"'
    val lineTerminator: String = "\r\n"
    val quoting: Quoting = QUOTE_NONE
    val treatEmptyLineAsNil: Boolean = false
  }
}