package json2CsvStream

import scala.annotation.tailrec
import scala.collection.SortedSet

import org.apache.commons.io.FilenameUtils

import com.github.tototoshi.csv.CSVWriter

import jawn.ast._
import jawn.ast.JParser._
import jawn.AsyncParser
import jawn.ParseException

import java.io.File

object Json2CsvStream {

  val nestedColumnnSeparator = "->"

  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the file path as argument ")
    else process(args(0))
  }

  def process(path: String): String = {
    val file = new File(path)
    if (!file.isFile()) {
      println("Error - the file " + path + " does not exists")
      System.exit(0)
    }

    val resultFileName = FilenameUtils.removeExtension(file.getName) + "-json.csv"
    val streamInput = scala.io.Source.fromFile(file, "UTF-8").getLines().toStream
    val rowCount = streamConversion(streamInput, resultFileName)

    println(s"Success - transformation completed in file $resultFileName")
    println(s"          $rowCount CSV rows generated")
    resultFileName
  }

  def streamConversion(chunks: Stream[String], resultFileName: String): Long = {
    val csvWriter = CSVWriter.open(resultFileName, append = true, encoding = "UTF-8")
    val p = jawn.Parser.async[JValue](mode = AsyncParser.UnwrapArray)

    def processJValue(j: JValue, resultAcc: ResultAcc): ResultAcc = j match {
      case JObject(values) ⇒
        val listTuple = loopOverKeys(values.toMap, "")
        val listOfKeys = listTuple.map(_._1).sorted
        val keysWithNesting = listOfKeys.distinct.filter(_.contains(nestedColumnnSeparator))
        val updatedKeysWithNestingSeen = resultAcc.keysWithNestingSeen ++ keysWithNesting.toSet

        val keysWithoutNesting = listOfKeys.distinct.filterNot { k ⇒
          // FIXME detecting the presence of a nestedColumnnSeparator String is not cool :(
          updatedKeysWithNestingSeen.contains(k) || updatedKeysWithNestingSeen.exists(_.startsWith(k + " " + nestedColumnnSeparator))
        }

        // Write headers if necessary
        val headers = keysWithoutNesting.sorted ::: updatedKeysWithNestingSeen.toList
        if (resultAcc.rowCount == 0) csvWriter.writeRow(headers)

        // Write rows
        val reconciliatedValues = reconciliateValues(headers, listTuple)
        val rowsNbWritten = writeRows(reconciliatedValues, keysWithoutNesting, updatedKeysWithNestingSeen.toList, csvWriter)

        ResultAcc(updatedKeysWithNestingSeen, rowsNbWritten)
      case _ ⇒
        println(s"other match? $j")
        ResultAcc()
    }

    def reconciliateValues(headers: List[String], listTuple: List[(String, JValue)]) = {
      val fakeValues = headers.filterNot(h ⇒ listTuple.exists(_._1 == h)).map(h ⇒ (h, JNull))
      val correctValues = listTuple.filter(c ⇒ headers.contains(c._1))
      correctValues.sortBy(_._1) ::: fakeValues.sortBy(_._1)
    }

    def loopOverKeys(values: Map[String, JValue], parentKey: String): List[(String, JValue)] = {
      values.map {
        case (k, v) ⇒ jvalueMatcher(v, k, parentKey)
      }.toList.flatten
    }

    def loopOverValues(values: List[JValue], key: String, parentKey: String): List[(String, JValue)] = {
      values.flatMap(jvalueMatcher(_, key, parentKey))
    }

    def jvalueMatcher(value: JValue, key: String, parentKey: String): List[(String, JValue)] = {
      val newKey = if (parentKey.isEmpty) key else s"$parentKey $nestedColumnnSeparator $key"
      value match {
        case j @ JNull             ⇒ List((newKey, j))
        case j @ JString(jvalue)   ⇒ List((newKey, j))
        case j @ LongNum(jvalue)   ⇒ List((newKey, j))
        case j @ DoubleNum(jvalue) ⇒ List((newKey, j))
        case j @ DeferNum(jvalue)  ⇒ List((newKey, j))
        case j @ JTrue             ⇒ List((newKey, j))
        case j @ JFalse            ⇒ List((newKey, j))
        case JObject(jvalue)       ⇒ loopOverKeys(jvalue.toMap, key)
        case JArray(jvalue) ⇒
          if (jvalue.isEmpty) List((key, JNull))
          else if (isJArrayOfValues(jvalue)) List((key, mergeJValue(jvalue.toList)))
          else loopOverValues(jvalue.toList, key, parentKey)
      }
    }

    def mergeJValue(values: List[JValue]): JValue = {
      val r = values.map { v ⇒
        v match {
          case j @ JNull             ⇒ ""
          case j @ JString(jvalue)   ⇒ jvalue
          case j @ LongNum(jvalue)   ⇒ jvalue.toString
          case j @ DoubleNum(jvalue) ⇒ jvalue.toString
          case j @ DeferNum(jvalue)  ⇒ jvalue.toString
          case j @ JTrue             ⇒ "true"
          case j @ JFalse            ⇒ "false"
          case _                     ⇒ "" // can"t merge other stuff
        }
      }.mkString(", ")
      JString(r)
    }

    def isJArrayOfValues(vs: Array[JValue]): Boolean = {
      vs.toList.forall {
        _ match {
          case JNull             ⇒ true
          case JString(jvalue)   ⇒ true
          case LongNum(jvalue)   ⇒ true
          case DoubleNum(jvalue) ⇒ true
          case DeferNum(jvalue)  ⇒ true
          case JTrue             ⇒ true
          case JFalse            ⇒ true
          case _                 ⇒ false
        }
      }
    }

    def writeRows(values: List[(String, JValue)], keysWithoutNesting: List[String], keysWithNesting: List[String], csvWriter: CSVWriter): Long = {
      val valuesWithoutNesting = values.sortBy(_._1).filter(v ⇒ keysWithoutNesting.contains(v._1)).map(_._2)
      val emptyFiller = keysWithoutNesting.map(v ⇒ JNull)
      val valuesWithNesting = values.sortBy(_._1).filter(v ⇒ keysWithNesting.contains(v._1))
      val groupedValues = valuesWithNesting.groupBy(_._1)

      val rowsNbToWrite: Int = {
        if (!keysWithNesting.isEmpty) {
          groupedValues.values.maxBy(_.length).size
        } else 1
      }

      for (i ← List.range(0, rowsNbToWrite)) {
        val extra = if (groupedValues.values.isEmpty) List()
        else groupedValues.toList.sortBy(_._1).map {
          case (k, values) ⇒ if (values.indices.contains(i)) values(i)._2 else JNull
        }

        if (i == 0) csvWriter.writeRow(valuesWithoutNesting ::: extra map (renderValue(_)))
        else csvWriter.writeRow(emptyFiller ::: extra map (renderValue(_)))
      }
      rowsNbToWrite
    }

    //https://github.com/non/jawn/issues/13
    def renderValue(v: JValue) = v.render(jawn.ast.FastRenderer).trim.replace("null", "").replace("[]", "")

    @tailrec
    def loop(st: Stream[String], p: jawn.AsyncParser[JValue], resultAcc: ResultAcc): ResultAcc = {
      st match {
        case Stream.Empty ⇒
          p.finish() match {
            case Left(exception) ⇒
              println(exception)
              ResultAcc()
            case Right(jsSeq) ⇒
              jsSeq.foldLeft(resultAcc) { (a, b) ⇒ a + processJValue(b, a) }
          }
        case s #:: tail ⇒
          p.absorb(s) match {
            case Left(exception) ⇒
              println(exception)
              ResultAcc()
            case Right(jsSeq) ⇒
              val newAcc = jsSeq.foldLeft(resultAcc) { (a, b) ⇒ a + processJValue(b, a) }
              loop(tail, p, newAcc)
          }
      }
    }

    // Business Time!
    val result = loop(chunks, p, ResultAcc())
    csvWriter.close()
    result.rowCount
  }
}

case class ResultAcc(keysWithNestingSeen: SortedSet[String] = SortedSet.empty[String], rowCount: Long = 0L) {
  def +(other: ResultAcc) = copy(keysWithNestingSeen ++ other.keysWithNestingSeen, rowCount + other.rowCount)
}