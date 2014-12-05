json-2-csv-stream [![Build Status](https://travis-ci.org/agourlay/json-2-csv-stream.png?branch=master)](https://travis-ci.org/agourlay/json-2-csv-stream)
=========


- transforms a file containing a collection of JSON into a CSV file.
- nested objects are turned into extra columns.
- works in a streaming fashion allowing the processing of very large files.
- the JSON objects at the collection level must share a common structure. 

## Status

Don't take this repo too seriously, it is still mostly a WIP.

## Limitations

- the first element should be a complete definition of the structure, the following ones can be sparse.

## Example

A json file containing a collection of one object like [this](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test.json) will be transformed into a CSV file like [that](https://github.com/agourlay/json-2-csv-stream/blob/master/src/test/resources/test-json.csv).

## Todos

- error handling.
- more unit-tests.
- proper logging.
- publish either as a lib or as a runnable jar.
