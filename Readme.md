json-2-csv-stream [![Build Status](https://travis-ci.org/agourlay/json-2-csv-stream.png?branch=master)](https://travis-ci.org/agourlay/json-2-csv-stream)
=========


## Features

- transforms a JSON collection loaded from a ```File``` or from a ```Stream[String]``` into a CSV file.
- nested objects are turned into extra columns and lines.
- works in a streaming fashion allowing the processing of very large files with a small memory footprint.

## Limitations

- the JSON objects at the collection level must share a common structure.
- the first element should be a complete definition of the structure, the following elements can be sparse.
- the transformation stops at the first error encountered.

## Input & output formats

A json file containing a collection like [this](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test.json) is transformed into a CSV file like [that](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test-json.csv).

When nested objects are turned into extra columns and the content of the parent objects is not repeated.

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
        val input = new File(args(0))
        val resultFileName = FilenameUtils.removeExtension(input.getName) + "-json.csv"
        val output = new FileOutputStream(resultFileName)
        Json2CsvStream.convert(input, output) match {
        	case Success(nb) => println(s"$nb CSV lines written")
        	case Failure(e)  => println(s"Something bad happened $e")
  	    }
    }
  }
}
```