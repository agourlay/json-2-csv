package com.github.agourlay.json2Csv

import com.github.tototoshi.csv.CSVWriter
import jawn.ast.JParser._
import jawn.ast._

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.collection.breakOut

private object Converter {

  def processJValue(j: JValue, progress: Progress, csvWriter: CSVWriter): Either[Exception, Progress] = j match {
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
      val rowsNbWritten = writeRows(reconcileValues(newKeys, cells), csvWriter)

      Right(Progress(newKeys, rowsNbWritten))
    case _ ⇒
      Left(new RuntimeException(s"Found a non JSON object - $j"))
  }

  def writeHeaders(headers: SortedSet[Key], csvWriter: CSVWriter) {
    csvWriter.writeRow(headers.map(_.physicalHeader)(breakOut))
  }

  def reconcileValues(keys: SortedSet[Key], cells: Array[Cell]): Array[Cell] = {
    val fakeValues: Array[Cell] = keys.filterNot(k ⇒ cells.exists(_.key == k)).map(k ⇒ Cell(k, JNull))(breakOut)
    val correctValues: Array[Cell] = cells.filter(c ⇒ keys.contains(c.key))
    correctValues ++: fakeValues
  }

  def loopOverKeys(values: Map[String, JValue], key: Key = Key.emptyKey): Array[Cell] = {
    val arrays: Array[Array[Cell]] = values.map {
      case (k, v) ⇒ jValueMatcher(v, key.addSegment(k))
    }(breakOut)
    arrays.flatten
  }

  def loopOverValues(values: Array[JValue], key: Key): Array[Cell] =
    values.flatMap(jValueMatcher(_, key))

  def jValueMatcher(value: JValue, key: Key): Array[Cell] =
    value match {
      case j @ JNull        ⇒ Array(Cell(key, j))
      case j @ JString(_)   ⇒ Array(Cell(key, j))
      case j @ LongNum(_)   ⇒ Array(Cell(key, j))
      case j @ DoubleNum(_) ⇒ Array(Cell(key, j))
      case j @ DeferNum(_)  ⇒ Array(Cell(key, j))
      case j @ DeferLong(_) ⇒ Array(Cell(key, j))
      case j @ JTrue        ⇒ Array(Cell(key, j))
      case j @ JFalse       ⇒ Array(Cell(key, j))
      case JObject(jvalue)  ⇒ loopOverKeys(jvalue.toMap, key)
      case JArray(jvalues) ⇒
        if (jvalues.isEmpty)
          Array(Cell(key, JNull))
        else if (isJArrayOfValues(jvalues))
          Array(Cell(key, mergeJValue(jvalues)))
        else
          loopOverValues(jvalues, key)
    }

  def mergeJValue(values: Array[JValue]): JValue = {
    val r = values.map {
      case JString(jvalue)   ⇒ jvalue
      case LongNum(jvalue)   ⇒ jvalue.toString
      case DoubleNum(jvalue) ⇒ jvalue.toString
      case DeferNum(jvalue)  ⇒ jvalue.toString
      case DeferLong(jvalue) ⇒ jvalue.toString
      case JTrue             ⇒ "true"
      case JFalse            ⇒ "false"
      case _                 ⇒ ""
    }.mkString(", ")
    JString(r)
  }

  def isJArrayOfValues(vs: Array[JValue]): Boolean =
    vs.forall {
      case JNull | JString(_) | LongNum(_) | DoubleNum(_) | DeferNum(_) | JTrue | JFalse ⇒ true
      case _                                                                             ⇒ false
    }

  def writeRows(values: Array[Cell], csvWriter: CSVWriter): Long = {
    val groupedValues = values.groupBy(_.key.physicalHeader)

    if (groupedValues.values.isEmpty)
      0
    else {
      val rowsNbToWrite = groupedValues.values.maxBy(_.length).length
      val sortedRows: Array[(String, Array[Cell])] = groupedValues.toArray.sortBy(_._1)

      for (i ← 0 until rowsNbToWrite) {
        val row: Array[JValue] = sortedRows.map {
          case (_, vs) ⇒ vs.lift(i).map(_.value).getOrElse(JNull)
        }

        csvWriter.writeRow(row.map(render))
        csvWriter.flush()
      }
      rowsNbToWrite
    }
  }

  //https://github.com/non/jawn/issues/13
  def render(v: JValue): String = v.render(jawn.ast.FastRenderer).trim.replace("null", "").replace("[]", "")

  @tailrec
  def consume(st: Stream[String], p: jawn.AsyncParser[JValue], w: CSVWriter, progress: Progress = Progress()): Either[Exception, Progress] =
    st match {
      case Stream.Empty ⇒
        p.finish().flatMap(jsSeq ⇒ processJValues(progress, jsSeq, w))
      case s #:: tail ⇒
        p.absorb(s) match {
          case Right(jsSeq) ⇒
            processJValues(progress, jsSeq, w) match {
              case Right(acc) ⇒ consume(tail, p, w, acc)
              case Left(e)    ⇒ Left(e)
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

case class Progress(keysSeen: SortedSet[Key] = SortedSet.empty[Key], rowCount: Long = 0L)

object Progress {
  def append(a: Progress, b: Progress): Progress = a.copy(a.keysSeen ++ b.keysSeen, a.rowCount + b.rowCount)
}

case class Key(segments: Vector[String]) {
  val physicalHeader = segments.mkString(Key.nestedColumnSeparator)
  def +(other: Key) = copy(segments ++: other.segments)
  def addSegment(other: String) = copy(segments :+ other)
}

object Key {
  val nestedColumnSeparator = "."
  val emptyKey = Key(Vector())
  def fromPhysicalKey(pKey: String) = Key(pKey.split(nestedColumnSeparator).toVector)
  implicit val orderingByPhysicalHeader: Ordering[Key] = Ordering.by(k ⇒ k.physicalHeader)
}

case class Cell(key: Key, value: JValue)