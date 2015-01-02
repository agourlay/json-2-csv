package json2CsvStream

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.util._

import com.github.tototoshi.csv.CSVWriter

import jawn.ast._
import jawn.ast.JParser._
import jawn.AsyncParser
import jawn.ParseException

import java.io.{ File, OutputStream }

private object Converter {

  def processJValue(j: JValue, progress: Progress, csvWriter: CSVWriter): Try[Progress] = j match {
    case JObject(values) ⇒
      val cells = loopOverKeys(values.toMap)
      // First element should contain complete schema
      val newKeys = {
        if (progress.keysSeen.isEmpty) progress.keysSeen ++ cells.map(_.key)
        else progress.keysSeen
      }

      // Write headers if necessary
      if (progress.rowCount == 0) writeHeaders(newKeys, csvWriter)

      // Write rows
      val reconciliatedValues = reconciliateValues(newKeys, cells)
      val rowsNbWritten = writeRows(reconciliatedValues, csvWriter)

      Success(Progress(newKeys, rowsNbWritten))
    case _ ⇒
      Failure(new RuntimeException(s"Found a non JSON object - $j"))
  }

  def writeHeaders(headers: SortedSet[Key], csvWriter: CSVWriter) {
    csvWriter.writeRow(headers.map(_.physicalHeader).toSeq)
  }

  def reconciliateValues(keys: SortedSet[Key], cells: Array[Cell]): Array[Cell] = {
    val fakeValues = keys.filterNot(k ⇒ cells.exists(_.key == k)).map(k ⇒ Cell(k, JNull))
    val correctValues = cells.filter(c ⇒ keys.contains(c.key))
    correctValues ++: fakeValues.toArray
  }

  def loopOverKeys(values: Map[String, JValue], key: Key = Key.emptyKey): Array[Cell] =
    values.map {
      case (k, v) ⇒ jvalueMatcher(v, key.addSegment(k))
    }.toArray.flatten

  def loopOverValues(values: Array[JValue], key: Key): Array[Cell] =
    values.flatMap(jvalueMatcher(_, key))

  def jvalueMatcher(value: JValue, key: Key): Array[Cell] =
    value match {
      case j @ JNull        ⇒ Array(Cell(key, j))
      case j @ JString(_)   ⇒ Array(Cell(key, j))
      case j @ LongNum(_)   ⇒ Array(Cell(key, j))
      case j @ DoubleNum(_) ⇒ Array(Cell(key, j))
      case j @ DeferNum(_)  ⇒ Array(Cell(key, j))
      case j @ JTrue        ⇒ Array(Cell(key, j))
      case j @ JFalse       ⇒ Array(Cell(key, j))
      case JObject(jvalue)  ⇒ loopOverKeys(jvalue.toMap, key)
      case JArray(jvalue) ⇒
        if (jvalue.isEmpty) Array(Cell(key, JNull))
        else if (isJArrayOfValues(jvalue)) Array(Cell(key, mergeJValue(jvalue)))
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

  def writeRows(values: Array[Cell], csvWriter: CSVWriter): Long = {
    val groupedValues = values.groupBy(_.physicalKey)
    val rowsNbToWrite = groupedValues.values.maxBy(_.length).size

    for (i ← 0 until rowsNbToWrite) {
      val row = if (groupedValues.values.isEmpty) Array()
      else groupedValues.toArray.sortBy(_._1).map {
        case (k, values) ⇒ values.lift(i).map(_.value).getOrElse(JNull)
      }

      csvWriter.writeRow(row.map(renderValue(_)))
      csvWriter.flush()
    }
    rowsNbToWrite
  }

  //https://github.com/non/jawn/issues/13
  def renderValue(v: JValue) = v.render(jawn.ast.FastRenderer).trim.replace("null", "").replace("[]", "")

  @tailrec
  def consume(st: Stream[String], p: jawn.AsyncParser[JValue], w: CSVWriter, progress: Progress = Progress()): Try[Progress] =
    st match {
      case Stream.Empty ⇒
        p.finish() match {
          case Left(ex) ⇒
            Failure(new RuntimeException(ex))
          case Right(jsSeq) ⇒
            Try {
              jsSeq.foldLeft(progress) { (a, b) ⇒ a + processJValue(b, a, w).get }
            }
        }
      case s #:: tail ⇒
        p.absorb(s) match {
          case Left(ex) ⇒
            Failure(new RuntimeException(ex))
          case Right(jsSeq) ⇒
            Try {
              jsSeq.foldLeft(progress) { (a, b) ⇒ a + processJValue(b, a, w).get }
            } match {
              case Success(acc) ⇒ consume(tail, p, w, acc)
              case Failure(e)   ⇒ Failure(e)
            }
        }
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