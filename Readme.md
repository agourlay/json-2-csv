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

A json file containing a collection of one object like this

```
[ 
  { 
    "a": "value a1",
    "b": 
      {
        "c": "value c1",
        "d": "value d1"
      }
    ,
    "e": [
      {
        "f": "value f11",
        "g": "value g11"
      },
      {
        "f": "value f12",
        "g": "value g12"
      }
    ],
    "i" : [
      {
        "j" : [ 
          {
            "k": "value k1",
            "l": "value l1"
          }
        ]
      }
    ]
  },
    { 
    "a": "value a2",
    "b": 
      {
        "c": "value c2",
        "d": "value d2"
      }
    ,
    "e": [],
    "i" : [
      {
        "j" : [ 
          {
            "k": "value k2",
            "l": "value l2"
          }
        ]
      }
    ]
  }
]
```

will be transformed into a 5 lines CSV file (formatted for the readme)

```
a       , b -> c  , b -> d  , e -> f   , e -> g   , j -> k  , j -> l
value a1, value c1, value d1, value f11, value g11, value k1, value l1
        ,         ,         , value f12, value g12,         ,
        ,         ,         , value f13, value g13,         ,
value a2, value c2, value d2,          ,          ,value k2, value l2

```

## Todos

- error handling
- more unit-tests.
- proper logging.
- extract column separator to config file.
- perfomance optim. (duplicate sorting, fusion mapping, usage of Vector...)
- publish either as a lib or as a runnable jar.
