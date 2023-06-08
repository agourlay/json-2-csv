package com.github.agourlay.json2Csv

import com.github.tototoshi.csv.CSVWriter
import org.typelevel.jawn.AsyncParser
import org.typelevel.jawn.ast.JParser._
import org.typelevel.jawn.ast._

import scala.annotation.tailrec

private object Converter {

  private val trueStr = "true"
  private val falseStr = "false"
  private val emptyStr = ""

  private def processJValue(j: JValue, progress: Progress, csvWriter: CSVWriter): Either[Exception, Progress] = j match {
    case JObject(fields) =>
      val cells = loopOverKeys(fields, Key.emptyKey).toArray
      // Write headers if necessary
      if (progress.rowCount == 0) {
        val newKeys = cells.iterator.map(_.key).toSet
        val headers = newKeys.iterator.map(_.physicalHeader).toSeq.sorted
        csvWriter.writeRow(headers)
        val rowsNbWritten = writeCells(cells, csvWriter)
        Right(Progress(newKeys, rowsNbWritten))
      } else {
        // Missing column will be faked as the first element should contain the complete schema
        val reconciledCells = reconcileValues(progress.keysSeen, cells)
        val rowsNbWritten = writeCells(reconciledCells, csvWriter)
        Right(Progress(progress.keysSeen, progress.rowCount + rowsNbWritten))
      }

    case _ =>
      Left(new IllegalArgumentException(s"Found a non JSON object - $j"))
  }

  private def reconcileValues(knownKeys: Set[Key], cells: Array[Cell]): Array[Cell] = {
    val fakeValues = knownKeys.iterator.collect {
      case k if !cells.exists(_.key.physicalHeader == k.physicalHeader) => Cell(k, JNull)
    }.toArray
    val correctValues = cells.filter(c => knownKeys.contains(c.key))
    if (fakeValues.isEmpty)
      correctValues
    else
      correctValues ++: fakeValues
  }

  // use initial mutable map from Jawn to avoid allocations
  private def loopOverKeys(fields: collection.mutable.Map[String, JValue], key: Key): Iterator[Cell] =
    fields.iterator.flatMap {
      case (k, v) => jValueMatcher(key.addSegment(k))(v)
    }

  private def iteratorOneCell(key: Key, value: JValue): Iterator[Cell] = Iterator.single(Cell(key, value))

  private def jValueMatcher(key: Key)(value: JValue): Iterator[Cell] =
    value match {
      case JObject(fields) =>
        loopOverKeys(fields, key)
      case JArray(values) =>
        if (values.isEmpty)
          iteratorOneCell(key, JNull)
        else if (isJArrayOfValues(values))
          iteratorOneCell(key, mergeJValue(values))
        else
          // recurse over JArray's values
          values.iterator.flatMap(jValueMatcher(key))
      case _ =>
        iteratorOneCell(key, value)
    }

  private def mergeJValue(values: Array[JValue]): JValue =
    JString {
      values.iterator.map {
        case JString(jvalue)   => jvalue
        case LongNum(jvalue)   => jvalue.toString
        case DoubleNum(jvalue) => jvalue.toString
        case DeferNum(jvalue)  => jvalue
        case DeferLong(jvalue) => jvalue
        case JTrue             => trueStr
        case JFalse            => falseStr
        case _                 => emptyStr
      }.mkString(", ")
    }

  private def isJArrayOfValues(vs: Array[JValue]): Boolean =
    vs.forall {
      case JNull | JString(_) | LongNum(_) | DoubleNum(_) | DeferNum(_) | JTrue | JFalse => true
      case _                                                                             => false
    }

  private def writeCells(values: Array[Cell], csvWriter: CSVWriter): Long = {
    val groupedArray: Seq[(String, Array[Cell])] = values.groupBy(_.key.physicalHeader).toSeq
    val rowsNbToWrite = groupedArray.maxBy(_._2.length)._2.length
    val sortedRows = groupedArray.sortBy(_._1)
    for (rowIndex <- 0 until rowsNbToWrite) {
      val row = sortedRows.map {
        case (_, vs) =>
          // Don't use Array.lift to avoid allocating an Option
          val json = if (rowIndex < vs.length) vs.apply(rowIndex).value else JNull
          render(json)
      }
      csvWriter.writeRow(row) //auto-flush
    }
    rowsNbToWrite
  }

  //https://github.com/typelevel/jawn/issues/13
  private def render(v: JValue): String = v match {
    case JArray(values) if values.isEmpty => emptyStr
    case JNull                            => emptyStr
    case _                                => v.render(FastRenderer)
  }

  @tailrec
  def consumeStream(st: LazyList[String], p: AsyncParser[JValue], w: CSVWriter)(progress: Progress): Either[Exception, Progress] =
    st match {
      case s #:: tail =>
        p.absorb(s) match {
          case Right(jsSeq) =>
            processJValues(progress, jsSeq, w) match {
              case Right(acc)  => consumeStream(tail, p, w)(acc)
              case l @ Left(_) => l
            }
          case Left(e) => Left(e)
        }
      case _ =>
        p.finish().flatMap(jsSeq => processJValues(progress, jsSeq, w))
    }

  private def processJValues(initProgress: Progress, jValues: collection.Seq[JValue], w: CSVWriter): Either[Exception, Progress] =
    jValues.foldLeft[Either[Exception, Progress]](Right(initProgress)) {
      case (eitherAcc, n) => eitherAcc.flatMap { acc => processJValue(n, acc, w) }
    }
}

case class Progress(keysSeen: Set[Key], rowCount: Long)

object Progress {
  val empty: Progress = Progress(Set.empty[Key], 0L)
}

case class Key(revertedSegments: List[String]) {
  val physicalHeader: String = revertedSegments.reverseIterator.mkString(Key.nestedColumnSeparator)
  def addSegment(other: String): Key = copy(other :: revertedSegments)
}

object Key {
  private val nestedColumnSeparator = "."
  val emptyKey: Key = Key(Nil)
}

case class Cell(key: Key, value: JValue)