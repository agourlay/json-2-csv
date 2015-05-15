json-2-csv-stream [![Build Status](https://travis-ci.org/agourlay/json-2-csv-stream.png?branch=master)](https://travis-ci.org/agourlay/json-2-csv-stream)  [ ![Download](https://api.bintray.com/packages/agourlay/maven/json-2-csv-stream/images/download.svg) ](https://bintray.com/agourlay/maven/json-2-csv-stream/_latestVersion)
=========

A library transforming JSON collections into CSV files.

## Features

- injest JSON collections from a ```File``` or from a ```Stream[String]```.
- nested JSON objects are turned into extra CSV columns and lines.
- works in a streaming fashion with a small memory footprint.

## Limitations

- the JSON objects in the collection level must share a common structure.
- the first element should be a complete definition of the structure, the following elements can be sparse.
- the transformation stops at the first error encountered.

## Input & output formats

A file containing a JSON collection like [this](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test.json) is transformed into a CSV file like [that](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test-json.csv).

When nested objects are turned into extra columns the content of the parent object is not repeated.

## APIs

Two methods on the ```Json2CsvStream``` object returning a ```Try``` of the number of CSV lines written to the ```OutputStream```:

```scala
def convert(file: File, resultOutputStream: OutputStream): Try[Long]

def convert(chunks: ⇒ Stream[String], resultOutputStream: OutputStream): Try[Long]
```

## Usage example as a standalone program

```scala
object Boot {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the file path as argument ")
    else {
        val output = new FileOutputStream("result-json.csv")
        Json2CsvStream.convert(new File(args(0)), output) match {
        	case Success(nb) => println(s"$nb CSV lines written")
        	case Failure(e)  => println(s"Something bad happened $e")
  	    }
    }
  }
}
```

## Installation

``` scala
resolvers += "agourlay at bintray" at "http://dl.bintray.com/agourlay/maven"

libraryDependencies ++= List(
  "com.github.agourlay" %% "json-2-csv-stream" % "0.1",
  ...
)
```

## License

This code is open source software licensed under the [MIT License]("http://opensource.org/licenses/MIT").