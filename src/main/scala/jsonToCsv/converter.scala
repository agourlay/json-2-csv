package json2CsvStream

import scala.annotation.tailrec
import scala.collection.SortedSet

import org.slf4j.LoggerFactory

import com.github.tototoshi.csv.CSVWriter

import jawn.ast._
import jawn.ast.JParser._
import jawn.AsyncParser
import jawn.ParseException

import java.io.{ File, OutputStream }

object Converter {

  def log = LoggerFactory.getLogger(this.getClass)

  def fileConversion(file: File, resultOutputStream: OutputStream): Long = {
    if (!file.isFile()) {
      log.error("The file " + file.getCanonicalPath() + " does not exists")
      System.exit(0)
    }
    val streamInput = scala.io.Source.fromFile(file, "UTF-8").getLines().toStream
    streamConversion(streamInput, resultOutputStream)
  }

  def streamConversion(chunks: Stream[String], resultOutputStream: OutputStream): Long = {
    val csvWriter = CSVWriter.open(resultOutputStream)
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
        case j @ JNull        ⇒ Vector(Cell(key, j))
        case j @ JString(_)   ⇒ Vector(Cell(key, j))
        case j @ LongNum(_)   ⇒ Vector(Cell(key, j))
        case j @ DoubleNum(_) ⇒ Vector(Cell(key, j))
        case j @ DeferNum(_)  ⇒ Vector(Cell(key, j))
        case j @ JTrue        ⇒ Vector(Cell(key, j))
        case j @ JFalse       ⇒ Vector(Cell(key, j))
        case JObject(jvalue)  ⇒ loopOverKeys(jvalue.toMap, key)
        case JArray(jvalue) ⇒
          if (jvalue.isEmpty) Vector(Cell(key, JNull))
          else if (isJArrayOfValues(jvalue)) Vector(Cell(key, mergeJValue(jvalue)))
          else loopOverValues(jvalue, key)
      }

    def mergeJValue(values: Array[JValue]): JValue = {
      val r = values.map { v ⇒
        v match {
          case JString(jvalue)   ⇒ jvalue
          case LongNum(jvalue)   ⇒ jvalue.toString
          case DoubleNum(jvalue) ⇒ jvalue.toString
          case DeferNum(jvalue)  ⇒ jvalue.toString
          case JTrue             ⇒ "true"
          case JFalse            ⇒ "false"
          case _                 ⇒ ""
        }
      }.mkString(", ")
      JString(r)
    }

    def isJArrayOfValues(vs: Array[JValue]): Boolean =
      vs.forall {
        _ match {
          case JNull | JString(_) | LongNum(_) | DoubleNum(_) | DeferNum(_) | JTrue | JFalse ⇒ true
          case _                                                                             ⇒ false
        }
      }

    def writeRows(values: Vector[Cell], keys: SortedSet[Key], csvWriter: CSVWriter): Long = {
      val groupedValues = values.groupBy(_.physicalKey)
      val rowsNbToWrite = groupedValues.values.maxBy(_.length).size

      for (i ← 0 until rowsNbToWrite) {
        val row = if (groupedValues.values.isEmpty) Vector()
        else groupedValues.toVector.sortBy(_._1).map {
          case (k, values) ⇒ values.lift(i).map(_.value).getOrElse(JNull)
        }

        if (i == 0) {
          csvWriter.writeRow(row map renderValue)
        } else {
          csvWriter.writeRow(row map renderValue)
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
    log.info(s"Conversion completed : $finalRowCount CSV rows generated")
    finalRowCount
  }
}

case class Progress(keysSeen: SortedSet[Key] = SortedSet.empty[Key], rowCount: Long = 0L) {
  def +(other: Progress) = copy(keysSeen ++ other.keysSeen, rowCount + other.rowCount)
}

case class Key(segments: Vector[String]) {
  val physicalHeader = segments.mkString(Key.nestedColumnnSeparator)
  def +(other: Key) = copy(segments ++: other.segments)
  def addSegment(other: String) = copy(segments :+ other)
}

object Key {
  val nestedColumnnSeparator = "."
  val emptyKey = Key(Vector())
  def fromPhysicalKey(pKey: String) = Key(pKey.split(nestedColumnnSeparator).toVector)
  implicit val orderingByPhysicalHeader: Ordering[Key] = Ordering.by(k ⇒ k.physicalHeader)
}

case class Cell(key: Key, value: JValue) {
  val physicalKey = key.physicalHeader
}