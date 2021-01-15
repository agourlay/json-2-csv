json-2-csv [![Build Status](https://travis-ci.org/agourlay/json-2-csv.svg?branch=master)](https://travis-ci.org/agourlay/json-2-csv) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/json-2-csv_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.agourlay/json-2-csv_2.13) [![Join the chat at https://gitter.im/agourlay/json-2-csv](https://badges.gitter.im/agourlay/json-2-csv.svg)](https://gitter.im/agourlay/json-2-csv?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
=========

A library transforming JSON collections into CSV files.

## Features

- ingests JSON collections from a `File` or from a `LazyList[String]`.
- turns nested JSON objects into extra CSV columns and lines.
- works in a streaming fashion with a small memory footprint.

## Limitations

- the JSON objects in the collection level must share a common structure.
- the first element should be a complete definition of the structure, the following elements can be sparse.
- the transformation stops at the first error encountered.

## Input & output formats

A file containing a JSON collection like [this](https://github.com/agourlay/json-2-csv/blob/master/src/test/resources/test.json) is transformed into a CSV file like [that](https://github.com/agourlay/json-2-csv/blob/master/src/test/resources/test-json.csv).

When nested objects are turned into extra columns the content of the parent object is not repeated.

## APIs

Two methods on the `Json2Csv` object returning an `Either` of the number of CSV lines written to the `OutputStream`:

```scala
def convert(file: File, resultOutputStream: OutputStream): Either[Exception, Long]

def convert(chunks: ⇒ LazyList[String], resultOutputStream: OutputStream): Either[Exception, Long]
```

## Usage example

```scala
object Boot {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the CSV file path as argument ")
    else {
      val output = new FileOutputStream("result-json.csv")
      Json2Csv.convert(new File(args(0)), output) match {
        case Right(nb) => println(s"$nb CSV lines written to 'result-json.csv'")
        case Left(e)  => println(s"Something bad happened $e")
      }
    }
  }
}
```

## Installation

``` scala
libraryDependencies ++= Seq(
  "com.github.agourlay" %% "json-2-csv" % "0.5.4",
  ...
)
```

## License

This code is open source software licensed under the [MIT License]("http://opensource.org/licenses/MIT").
