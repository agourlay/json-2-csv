package com.github.agourlay.json2Csv

import java.util.regex.Pattern

import com.github.tototoshi.csv.CSVWriter
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.ast.JParser._
import org.typelevel.jawn.ast._

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.collection.breakOut

private object Converter {

  private val trueStr = "true"
  private val falseStr = "false"
  private val emptyStr = ""

  private val nullStr = Pattern.compile("null")
  private val emptyArrayStr = Pattern.compile(Pattern.quote("[]"))

  def processJValue(j: JValue, progress: Progress, csvWriter: CSVWriter): Either[Exception, Progress] = j match {
    case JObject(fields) ⇒
      val cells = loopOverKeys(fields, Key.emptyKey)
      // First element should contain complete schema
      val newKeys = {
        if (progress.keysSeen.isEmpty) progress.keysSeen ++ cells.map(_.key)
        else progress.keysSeen
      }

      // Write headers if necessary
      if (progress.rowCount == 0) csvWriter.writeRow(newKeys.map(_.physicalHeader)(breakOut))

      // Write rows
      val rowsNbWritten = writeRows(reconcileValues(newKeys, cells), csvWriter)

      Right(Progress(newKeys, rowsNbWritten))

    case _ ⇒
      Left(new IllegalArgumentException(s"Found a non JSON object - $j"))
  }

  def reconcileValues(keys: SortedSet[Key], cells: Array[Cell]): Array[Cell] = {
    val fakeValues: Array[Cell] = keys.collect {
      case k if !cells.exists(_.key.physicalHeader == k.physicalHeader) ⇒ Cell(k, JNull)
    }(breakOut)
    val correctValues: Array[Cell] = cells.filter(c ⇒ keys.contains(c.key))
    if (fakeValues.isEmpty)
      correctValues
    else
      correctValues ++: fakeValues
  }

  // use initial mutable map from Jawn to avoid allocations
  def loopOverKeys(fields: collection.mutable.Map[String, JValue], key: Key): Array[Cell] =
    fields.flatMap {
      case (k, v) ⇒ jValueMatcher(key.addSegment(k))(v)
    }(breakOut)

  def arrayOneCell(key: Key, value: JValue): Array[Cell] = {
    val array = Array.ofDim[Cell](1)
    array(0) = Cell(key, value)
    array
  }

  def jValueMatcher(key: Key)(value: JValue): Array[Cell] =
    value match {
      case JObject(fields) ⇒
        loopOverKeys(fields, key)
      case JArray(values) ⇒
        if (values.isEmpty)
          arrayOneCell(key, JNull)
        else if (isJArrayOfValues(values))
          arrayOneCell(key, mergeJValue(values))
        else
          // recurse over JArray's values
          values.flatMap(jValueMatcher(key))
      case _ ⇒
        arrayOneCell(key, value)
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

  def writeRows(values: Array[Cell], csvWriter: CSVWriter): Long = {
    val groupedArray: Array[(String, Array[Cell])] = values.groupBy(_.key.physicalHeader).toArray
    val rowsNbToWrite = groupedArray.maxBy(_._2.length)._2.length
    val sortedRows = groupedArray.sortBy(_._1)
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

  //https://github.com/typelevel/jawn/issues/13
  def render(v: JValue): String = {
    val r = v.render(FastRenderer)
    val r1 = nullStr.matcher(r).replaceAll(emptyStr)
    val r2 = emptyArrayStr.matcher(r1).replaceAll(emptyStr)
    r2
  }

  @tailrec
  def consume(st: Stream[String], p: AsyncParser[JValue], w: CSVWriter)(progress: Progress): Either[Exception, Progress] =
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
    def ghettoFoldMapTraverse[A, B](seq: Seq[A], init: B, merger: (B, B) ⇒ B)(f: (B, A) ⇒ Either[Exception, B]): Either[Exception, B] =
      seq.foldLeft[Either[Exception, B]](Right(init)) {
        case (eitherAcc, n) ⇒
          eitherAcc.flatMap { acc ⇒
            f(acc, n).map(merger(acc, _))
          }
      }

    ghettoFoldMapTraverse(jvalues, initProgress, Progress.append)((a, b) ⇒ processJValue(b, a, w))
  }

}

case class Progress(keysSeen: SortedSet[Key], rowCount: Long)

object Progress {
  val empty = Progress(SortedSet.empty[Key], 0L)
  def append(a: Progress, b: Progress): Progress = Progress(a.keysSeen ++ b.keysSeen, a.rowCount + b.rowCount)
}

case class Key(revertedSegments: List[String]) {
  val physicalHeader: String = revertedSegments.reverse.mkString(Key.nestedColumnSeparator)
  def addSegment(other: String): Key = copy(other :: revertedSegments)
}

object Key {
  private val nestedColumnSeparator = "."
  val emptyKey = Key(Nil)
  implicit val orderingByPhysicalHeader: Ordering[Key] = Ordering.by(_.physicalHeader)
}

case class Cell(key: Key, value: JValue)