package json2CsvStream

import java.io.File

object Boot {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the file path as argument ")
    else Converter.fileConversion(new File(args(0)))
  }
}