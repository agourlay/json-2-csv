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
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicLong }

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
    val csvWriter = CSVWriter.open(resultFileName, append = true, encoding = "UTF-8")
    val rowCount = streamConversion(file, csvWriter)
    csvWriter.close()

    println(s"Success - transformation completed in file $resultFileName")
    println(s"          $rowCount CSV rows generated")
    resultFileName
  }

  def streamConversion(file: File, csvWriter: CSVWriter): Long = {
    // Some cool Atomic vars :)
    var firstRow = new AtomicBoolean(true)
    var rowCount = new AtomicLong(0)

    val chunks = scala.io.Source.fromFile(file, "UTF-8").getLines().toStream
    val p = jawn.Parser.async[JValue](mode = AsyncParser.UnwrapArray)

    def processJValue(j: JValue, keysWithNestingSeen: SortedSet[String]): SortedSet[String] = j match {
      case JObject(values) ⇒
        val listTuple = loopOverKeys(values.toMap, "")
        val listOfKeys = listTuple.map(_._1).sorted
        val keysWithNesting = listOfKeys.distinct.filter(_.contains(nestedColumnnSeparator))
        val updatedKeysWithNestingSeen = keysWithNestingSeen ++ keysWithNesting.toSet

        val keysWithoutNesting = listOfKeys.distinct.filterNot { k ⇒
          updatedKeysWithNestingSeen.contains(k) || updatedKeysWithNestingSeen.exists(_.startsWith(k + " " + nestedColumnnSeparator))
        }

        val headers = keysWithoutNesting.sorted ::: updatedKeysWithNestingSeen.toList
        val reconciliatedValues = reconciliateValues(headers, listTuple)

        writeHeaders(headers, csvWriter)
        writeRows(reconciliatedValues, keysWithoutNesting, updatedKeysWithNestingSeen.toList, csvWriter)

        updatedKeysWithNestingSeen
      case _ ⇒
        println(s"other match? $j")
        SortedSet.empty[String]
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

    def writeHeaders(headers: List[String], csvWriter: CSVWriter) = {
      if (firstRow.get) {
        csvWriter.writeRow(headers)
        firstRow.set(false)
      }
    }

    def writeRows(values: List[(String, JValue)], keysWithoutNesting: List[String], keysWithNesting: List[String], csvWriter: CSVWriter) {
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
      rowCount.addAndGet(rowsNbToWrite)
    }

    //https://github.com/non/jawn/issues/13
    def renderValue(v: JValue) = v.render(jawn.ast.FastRenderer).trim.replace("null", "").replace("[]", "")

    @tailrec
    def loop(st: Stream[String], p: jawn.AsyncParser[JValue], keysWithNestingSeen: SortedSet[String]): Unit = {
      st match {
        case Stream.Empty ⇒
          p.finish().fold(e ⇒ println(e), jsSeq ⇒ jsSeq.foreach(processJValue(_, keysWithNestingSeen)))
        case s #:: tail ⇒
          p.absorb(s) match {
            case Left(exception) ⇒ println(exception)
            case Right(jsSeq) ⇒
              val newkeysWithNestingSeen = jsSeq.foldLeft(keysWithNestingSeen) { (a, b) ⇒
                a ++ processJValue(b, keysWithNestingSeen)
              }
              loop(tail, p, newkeysWithNestingSeen)
          }
      }
    }

    // Business Time!
    loop(chunks, p, SortedSet.empty[String])
    rowCount.get
  }
}