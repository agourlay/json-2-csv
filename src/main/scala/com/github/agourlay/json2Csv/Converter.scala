package com.github.agourlay.json2Csv

import com.github.tototoshi.csv.CSVWriter
import jawn.ast.JParser._
import jawn.ast._

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.collection.breakOut

private object Converter {

  private val trueStr = "true"
  private val falseStr = "false"
  private val emptyStr = ""
  private val nullStr = "null"
  private val emptyArrayStr = "[]"

  def processJValue(j: JValue, progress: Progress, csvWriter: CSVWriter): Either[Exception, Progress] = j match {
    case JObject(values) ⇒
      val cells = loopOverKeys(values)
      // First element should contain complete schema
      val newKeys = {
        if (progress.keysSeen.isEmpty) progress.keysSeen ++ cells.map(_.key)
        else progress.keysSeen
      }

      // Write headers if necessary
      if (progress.rowCount == 0) writeHeaders(newKeys, csvWriter)

      // Write rows
      val rowsNbWritten = writeRows(reconcileValues(newKeys, cells), csvWriter)

      Right(Progress(newKeys, rowsNbWritten))
    case _ ⇒
      Left(new RuntimeException(s"Found a non JSON object - $j"))
  }

  def writeHeaders(headers: SortedSet[Key], csvWriter: CSVWriter) {
    csvWriter.writeRow(headers.map(_.physicalHeader)(breakOut))
  }

  def reconcileValues(keys: SortedSet[Key], cells: Array[Cell]): Array[Cell] = {
    val fakeValues: Array[Cell] = keys.collect {
      case k if !cells.exists(_.key == k) ⇒ Cell(k, JNull)
    }(breakOut)
    val correctValues: Array[Cell] = cells.filter(c ⇒ keys.contains(c.key))
    correctValues ++: fakeValues
  }

  // use initial mutable map from Jawn to avoid allocations
  def loopOverKeys(values: collection.mutable.Map[String, JValue], key: Key = Key.emptyKey): Array[Cell] = {
    val arrays: Array[Array[Cell]] = values.map {
      case (k, v) ⇒ jValueMatcher(key.addSegment(k))(v)
    }(breakOut)
    arrays.flatten
  }

  def loopOverValues(values: Array[JValue], key: Key): Array[Cell] =
    values.flatMap(jValueMatcher(key))

  def jValueMatcher(key: Key)(value: JValue): Array[Cell] =
    value match {
      case j @ JNull        ⇒ Array(Cell(key, j))
      case j @ JString(_)   ⇒ Array(Cell(key, j))
      case j @ LongNum(_)   ⇒ Array(Cell(key, j))
      case j @ DoubleNum(_) ⇒ Array(Cell(key, j))
      case j @ DeferNum(_)  ⇒ Array(Cell(key, j))
      case j @ DeferLong(_) ⇒ Array(Cell(key, j))
      case j @ JTrue        ⇒ Array(Cell(key, j))
      case j @ JFalse       ⇒ Array(Cell(key, j))
      case JObject(jvalue)  ⇒ loopOverKeys(jvalue, key)
      case JArray(jvalues) ⇒
        if (jvalues.isEmpty)
          Array(Cell(key, JNull))
        else if (isJArrayOfValues(jvalues))
          Array(Cell(key, mergeJValue(jvalues)))
        else
          loopOverValues(jvalues, key)
    }

  def mergeJValue(values: Array[JValue]): JValue =
    JString {
      values.map {
        case JString(jvalue)   ⇒ jvalue
        case LongNum(jvalue)   ⇒ jvalue.toString
        case DoubleNum(jvalue) ⇒ jvalue.toString
        case DeferNum(jvalue)  ⇒ jvalue.toString
        case DeferLong(jvalue) ⇒ jvalue.toString
        case JTrue             ⇒ trueStr
        case JFalse            ⇒ falseStr
        case _                 ⇒ emptyStr
      }.mkString(", ")
    }

  def isJArrayOfValues(vs: Array[JValue]): Boolean =
    vs.forall {
      case JNull | JString(_) | LongNum(_) | DoubleNum(_) | DeferNum(_) | JTrue | JFalse ⇒ true
      case _                                                                             ⇒ false
    }

  def writeRows(values: Array[Cell], csvWriter: CSVWriter): Long =
    if (values.isEmpty)
      0
    else {
      val grouped = values.groupBy(_.key.physicalHeader)
      val rowsNbToWrite = grouped.values.maxBy(_.length).length
      val sortedRows: Array[(String, Array[Cell])] = grouped.toArray.sortBy(_._1)
      var rowIndex = 0
      while (rowIndex < rowsNbToWrite) {
        val row: Array[String] = sortedRows.map {
          case (_, vs) ⇒
            // Don't use Array.lift to avoid allocating an Option
            val json = if (rowIndex < vs.length) vs.apply(rowIndex).value else JNull
            render(json)
        }
        csvWriter.writeRow(row)
        csvWriter.flush()
        rowIndex += 1
      }
      rowsNbToWrite
    }

  //https://github.com/non/jawn/issues/13
  def render(v: JValue): String =
    v.render(jawn.ast.FastRenderer)
      .trim
      .replace(nullStr, emptyStr)
      .replace(emptyArrayStr, emptyStr)

  @tailrec
  def consume(st: Stream[String], p: jawn.AsyncParser[JValue], w: CSVWriter)(progress: Progress): Either[Exception, Progress] =
    st match {
      case Stream.Empty ⇒
        p.finish().flatMap(jsSeq ⇒ processJValues(progress, jsSeq, w))
      case s #:: tail ⇒
        p.absorb(s) match {
          case Right(jsSeq) ⇒
            processJValues(progress, jsSeq, w) match {
              case Right(acc)  ⇒ consume(tail, p, w)(acc)
              case l @ Left(_) ⇒ l
            }
          case Left(e) ⇒ Left(e)
        }
    }

  def processJValues(initProgress: Progress, jvalues: Seq[JValue], w: CSVWriter): Either[Exception, Progress] = {
    // Ghetto traverse foldMap
    def eitherTraverse[A, B](seq: Seq[A], init: B, merger: (B, B) ⇒ B)(f: (B, A) ⇒ Either[Exception, B]): Either[Exception, B] = {
      @tailrec
      def loop(ops: Seq[A], acc: B): Either[Exception, B] =
        if (ops.isEmpty)
          Right(acc)
        else
          f(acc, ops.head) match {
            case Right(h) ⇒ loop(ops.tail, merger(acc, h))
            case Left(e)  ⇒ Left(e)
          }

      loop(seq, init)
    }

    eitherTraverse(jvalues, initProgress, Progress.append)((a, b) ⇒ processJValue(b, a, w))
  }

}

case class Progress(keysSeen: SortedSet[Key], rowCount: Long)

object Progress {
  val empty = Progress(SortedSet.empty[Key], 0L)
  def append(a: Progress, b: Progress): Progress = a.copy(a.keysSeen ++ b.keysSeen, a.rowCount + b.rowCount)
}

case class Key(segments: Vector[String]) {
  val physicalHeader = segments.mkString(Key.nestedColumnSeparator)
  def addSegment(other: String) = copy(segments :+ other)
}

object Key {
  val nestedColumnSeparator = "."
  val emptyKey = Key(Vector.empty)
  implicit val orderingByPhysicalHeader: Ordering[Key] = Ordering.by(_.physicalHeader)
}

case class Cell(key: Key, value: JValue)