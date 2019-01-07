package com.github.agourlay.json2Csv

import java.io.{ File, FileNotFoundException, OutputStream }

import com.github.tototoshi.csv.{ CSVFormat, CSVWriter, QUOTE_NONE, Quoting }
import org.typelevel.jawn.{ AsyncParser, Parser }
import org.typelevel.jawn.ast.JParser._
import org.typelevel.jawn.ast.JValue

import scala.io.Source

object Json2Csv {

  def convert(file: File, resultOutputStream: OutputStream): Either[Exception, Long] =
    if (file.isFile)
      convert(Source.fromFile(file, "UTF-8").getLines().toStream, resultOutputStream)
    else
      Left(new FileNotFoundException("The file " + file.getCanonicalPath + " does not exists"))

  def convert(chunks: ⇒ Stream[String], resultOutputStream: OutputStream): Either[Exception, Long] = {
    val csvWriter = CSVWriter.open(resultOutputStream)(jsonCSVFormat)
    val parser = Parser.async[JValue](mode = AsyncParser.UnwrapArray)
    val finalProgress = Converter.consume(chunks, parser, csvWriter)(Progress.empty)
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