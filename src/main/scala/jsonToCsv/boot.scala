package json2CsvStream

import java.io._
import org.apache.commons.io.FilenameUtils

object Boot {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the file path as argument ")
    else {
      val input = new File(args(0))
      val resultFileName = FilenameUtils.removeExtension(input.getName) + "-json.csv"
      val output = new FileOutputStream(resultFileName)
      Converter.fileConversion(input, output)
    }
  }
}