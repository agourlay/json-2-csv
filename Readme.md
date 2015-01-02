json-2-csv-stream [![Build Status](https://travis-ci.org/agourlay/json-2-csv-stream.png?branch=master)](https://travis-ci.org/agourlay/json-2-csv-stream)
=========


## Features

- transforms a file containing a JSON collection into a CSV file.
- nested objects are turned into extra columns.
- works in a streaming fashion allowing the processing of very large files.

## Limitations

- the JSON objects at the collection level must share a common structure.
- the first element should be a complete definition of the structure, the following elements can be sparse.
- the transformation stops at the first error encountered.

## Input & output formats

A json file containing a collection of one object like [this](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test.json) will be transformed into a CSV file like [that](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test-json.csv).

## APIs

Two methods on the ```Json2CsvStream``` object returning a ```Try``` of the number of CSV lines written:

```
def convert(file: File, resultOutputStream: OutputStream): Try[Long]

def convert(chunks: ⇒ Stream[String], resultOutputStream: OutputStream): Try[Long]
```

## Usage example as a standalone program

```
object Boot {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty) println("Error - Provide the file path as argument ")
    else {
      val input = new File(args(0))
      val resultFileName = FilenameUtils.removeExtension(input.getName) + "-json.csv"
      val output = new FileOutputStream(resultFileName)
      Json2CsvStream.convert(input, output)
    }
  }
}
```