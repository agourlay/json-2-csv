package json2CsvStream

import scala.annotation.tailrec
import scala.collection.SortedSet

import org.apache.commons.io.FilenameUtils

import org.slf4j.LoggerFactory

import com.github.tototoshi.csv.CSVWriter

import jawn.ast._
import jawn.ast.JParser._
import jawn.AsyncParser
import jawn.ParseException

import java.io.File

object Converter {

  def log = LoggerFactory.getLogger(this.getClass)

  def fileConversion(file: File): File = {
    if (!file.isFile()) {
      log.error("The file " + file.getCanonicalPath() + " does not exists")
      System.exit(0)
    }

    val resultFileName = FilenameUtils.removeExtension(file.getName) + "-json.csv"
    val streamInput = scala.io.Source.fromFile(file, "UTF-8").getLines().toStream
    val rowCount = streamConversion(streamInput, resultFileName)
    new File(resultFileName)
  }

  def streamConversion(chunks: Stream[String], resultFileName: String): Long = {
    val csvWriter = CSVWriter.open(resultFileName, append = true, encoding = "UTF-8")
    val p = jawn.Parser.async[JValue](mode = AsyncParser.UnwrapArray)

    def processJValue(j: JValue, progress: Progress): Progress = j match {
      case JObject(values) ⇒
        val cells = loopOverKeys(values.toMap)
        // First element should contain complete schema
        val newKeys = {
          if (progress.keysSeen.isEmpty) progress.keysSeen ++ cells.map(_.key)
          else progress.keysSeen
        }

        // Write headers if necessary
        if (progress.rowCount == 0) writeHeaders(newKeys)

        // Write rows
        val reconciliatedValues = reconciliateValues(newKeys, cells)
        val rowsNbWritten = writeRows(reconciliatedValues, newKeys, csvWriter)

        Progress(newKeys, rowsNbWritten)
      case _ ⇒
        log.warn(s"Parsing something else $j")
        progress
    }

    def writeHeaders(headers: SortedSet[Key]) {
      csvWriter.writeRow(headers.map(_.physicalHeader).toSeq)
    }

    def reconciliateValues(keys: SortedSet[Key], cells: Vector[Cell]): Vector[Cell] = {
      val fakeValues = keys.filterNot(k ⇒ cells.exists(_.key == k)).map(k ⇒ Cell(k, JNull))
      val correctValues = cells.filter(c ⇒ keys.contains(c.key))
      correctValues.toVector ++: fakeValues.toVector
    }

    def loopOverKeys(values: Map[String, JValue], key: Key = Key.emptyKey): Vector[Cell] =
      values.map {
        case (k, v) ⇒ jvalueMatcher(v, key.addSegment(k))
      }.toVector.flatten

    def loopOverValues(values: Array[JValue], key: Key): Vector[Cell] =
      values.flatMap(jvalueMatcher(_, key)).toVector

    def jvalueMatcher(value: JValue, key: Key): Vector[Cell] =
      value match {
        case j @ JNull             ⇒ Vector(Cell(key, j))
        case j @ JString(jvalue)   ⇒ Vector(Cell(key, j))
        case j @ LongNum(jvalue)   ⇒ Vector(Cell(key, j))
        case j @ DoubleNum(jvalue) ⇒ Vector(Cell(key, j))
        case j @ DeferNum(jvalue)  ⇒ Vector(Cell(key, j))
        case j @ JTrue             ⇒ Vector(Cell(key, j))
        case j @ JFalse            ⇒ Vector(Cell(key, j))
        case JObject(jvalue)       ⇒ loopOverKeys(jvalue.toMap, key)
        case JArray(jvalue) ⇒
          if (jvalue.isEmpty) Vector(Cell(key, JNull))
          else if (isJArrayOfValues(jvalue)) Vector(Cell(key, mergeJValue(jvalue)))
          else loopOverValues(jvalue, key)
      }

    def mergeJValue(values: Array[JValue]): JValue = {
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

    def isJArrayOfValues(vs: Array[JValue]): Boolean =
      vs.forall {
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

    def writeRows(values: Vector[Cell], keys: SortedSet[Key], csvWriter: CSVWriter): Long = {
      val keysWithNesting = keys.filter(_.isNested)
      val keysWithoutNesting = keys.filter(!_.isNested)
      val valuesWithNesting = values.filter(v ⇒ keysWithNesting.contains(v.key))
      val groupedValues = valuesWithNesting.groupBy(_.physicalKey)

      val rowsNbToWrite = {
        if (!keysWithNesting.isEmpty) {
          groupedValues.values.maxBy(_.length).size
        } else 1
      }

      for (i ← 0 until rowsNbToWrite) {
        val extra = if (groupedValues.values.isEmpty) Vector()
        else groupedValues.toList.sortBy(_._1).map {
          case (k, values) ⇒ if (values.indices.contains(i)) values(i).value else JNull
        }

        if (i == 0) {
          val valuesWithoutNesting = values.filter(v ⇒ keysWithoutNesting.contains(v.key)).map(_.value)
          csvWriter.writeRow(valuesWithoutNesting ++: extra map (renderValue(_)))
        } else {
          val emptyFiller = keysWithoutNesting.map(v ⇒ JNull)
          csvWriter.writeRow(emptyFiller ++: extra map (renderValue(_)))
          csvWriter.flush()
        }
      }
      rowsNbToWrite
    }

    //https://github.com/non/jawn/issues/13
    def renderValue(v: JValue) = v.render(jawn.ast.FastRenderer).trim.replace("null", "").replace("[]", "")

    @tailrec
    def loop(st: Stream[String], p: jawn.AsyncParser[JValue], progress: Progress = Progress()): Progress =
      st match {
        case Stream.Empty ⇒
          p.finish() match {
            case Left(exception) ⇒
              log.error("An error occured at the end of the stream", exception)
              progress
            case Right(jsSeq) ⇒
              jsSeq.foldLeft(progress) { (a, b) ⇒ a + processJValue(b, a) }
          }
        case s #:: tail ⇒
          p.absorb(s) match {
            case Left(exception) ⇒
              log.error("An error occured in the stream", exception)
              progress
            case Right(jsSeq) ⇒
              val newAcc = jsSeq.foldLeft(progress) { (a, b) ⇒ a + processJValue(b, a) }
              loop(tail, p, newAcc)
          }
      }

    // Business Time!
    val finalRowCount = loop(chunks, p).rowCount
    csvWriter.close()
    log.info(s"Success - transformation completed in file $resultFileName")
    log.info(s"          $finalRowCount CSV rows generated")
    finalRowCount
  }
}

case class Progress(keysSeen: SortedSet[Key] = SortedSet.empty[Key], rowCount: Long = 0L) {
  def +(other: Progress) = copy(keysSeen ++ other.keysSeen, rowCount + other.rowCount)
}

case class Key(segments: Vector[String]) {
  val isNested = segments.size > 0
  val physicalHeader = segments.mkString(Key.nestedColumnnSeparator)
  def +(other: Key) = copy(segments ++: other.segments)
  def addSegment(other: String) = copy(segments :+ other)
}

object Key {
  val nestedColumnnSeparator = "."
  def emptyKey = Key(Vector())
  def fromPhysicalKey(pKey: String) = Key(pKey.split(nestedColumnnSeparator).toVector)
  implicit val orderingByPhysicalHeader: Ordering[Key] = Ordering.by(k ⇒ k.physicalHeader)
}

case class Cell(key: Key, value: JValue) {
  val physicalKey = key.physicalHeader
}