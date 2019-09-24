# DS Data Format.

The DS Data Format describes the data, which Mantik can manage.

It consists of a

* Definition of Data
* Definition of how Data can be serialized.

**Note:** The format is not yet stable and subject of changes.

## Design Ideas and Goals

Using the format, the following goals should be archived

The format itself should

* be human readable (at least in one serialization format and in it's definition)
* language / software stack agnostic
* be tolerant with changes of data structures
  
  It should be easy for a machine to adapt an old format to a new format.
  
* be extensible
* be composable
* be completely typed
* support the main types used in ML Applications
* support the generation of (trivial) adapters using simple pattern matching
* be table based to support simple transformations in SQL-like way, inspired from Apache Spark.

The serialization format should

* be fast when serializing / deserializing
* support a human readable notation for debugging
* be stream able
* support binary data

## Serialization

The serialization is done using MessagePack stream or JSON. 

### Message Pack Stream

The message pack stream consists of multiple elements. The first is a header of the following format:

```
{
    "format": // The Mantik format type, usually a table.
}
```

(In it's MessagePack analogy of JSON)


Afterwards the elements are sent after another. Usually they are Table Rows, and each row is 
expressed as Array of the serialization of the elements.

The serialization of the types is described in the types itself.

### JSON Bundle

The JSON Bundle is a slightly different, because it should be valid JSON, and JSON does not understand streams.

The encoding is as like:

```
{
    "type": // Mantik Data Type format
    "value": // The value of the bundle.
}
```
The `value` is usually an array of arrays, when encoding tabular data.
For single elements, it's just the single element.

Serialization itself can be piped through GZip, however it's enough if services just accept
GZIP-Compressed HTTP.

### Example for Serialization

A table of coordinates with names and sample image could be expressed like this in MessagePack

```
{
    "format": {
        "columns": {
            "x": "float64",
            "y": "float64",
            "name": "string",
            "icon": {
                "type": image,
                "width": 100,
                "height": 100,
                "componentType: {
                    "red": "uint8",
                    "blue": "uint8",
                    "green": "uint8"
                },
                "format": "png"
            }
        }
    }   
}
[1.2, 5.4, "Berlin", "4g3th43thkve55435"]
[5.3, 6.3, "Potsdam", "u634u34f0wg0ewg4"]
```

in JSON it looks like this: 
```
{
    "type": {
        "columns": {
            "x": "float64",
            "y": "float64",
            "name": "string",
            "icon": {
                "type": image,
                "width": 100,
                "height": 100,
                "componentType: {
                    "red": "uint8",
                    "blue": "uint8",
                    "green": "uint8"
                },
                "format": "png"
            }
        }
    },
    "value": [
        [1.2, 5.4, "Berlin", "4g3th43thkve55435"],
        [5.3, 6.3, "Potsdam", "u634u34f0wg0ewg4"]
    ]   
}

```

The Base64-Encoding of the image in the example is not valid.


## Types

The data description is using JSON as main format. 
YAML is also supported and will be converted to JSON first.

It consists of a set of fundamental types, and some composed data structures.

However the main structure is a table, as tables are very useful when it comes to 
many data elements of the same shape.

### Fundamental types

There are some fundamental types which are expressed using JSON-Strings (e.g. `"uint8"`) and correspond 
to their C++/Golang Counterpart.

There are

* `uint8`
* `int8`
* `uint32`
* `int32`
* `uint64`
* `int64`
* `bool`
* `float32`
* `float64`
* `string`
* `void`

The serialization is done with the default encoding in MsgPack and JSON.

### Table Type

The table types is expressed using a JSON Object with the `type: "tabular"` field. 
Tables are the only composed objects where `type` is optional.

The order of the columns plays a role during serialization.
The column names should be distinct.

Example:
```
{
    "type": "tabular", // Optional for Tables.
    "columns": { 
        "x" : "int8",
        "y" : "string"
    },
    "rowCount": 400
}
```

The `rowCount` element is optional and only specified if the number of rows is clear.

The serialization of the a row is done using an Array of the serialization of the cells.
When tables are embedded in other tables, they are serialized as Array of Rows, which are again
arrays of serialized cells.

### Image Type

The image type represents image data. They are expressed like 

```
{
    "type": "image",
    "width": "100",
    "height": "200",
    "components": {
        "red": {
            "componentType": "uint8"
        },
        ...
    },
    "format": "plain" // is the default and can be omitted.
}
```

The ordering of the components plays a role in serialization.
Possible components are `red`, `blue`, `green`, `black`. 

For the component type, only fixed-length-types of fixed byte length (no `string`, no `bool`!) are supported.

The encoding is done as a Byte Array of all image rows from upper left to lower right coordinates.

The encoding is done row wise.

The components are encoded in big endian.

The JSON Encoding is a Base64-String.

Alternatively, there can be a format specifier in `format`. The following values are supported

- `"png"` Then the transport is done using the binary representation in `PNG`-Format
- `"jpeg"` **Not yet implemented:** Then the transport is done using the binary representation in `JPEG`-Format

### Tensor Type

The Tensor type exists for compatibility with TensorFlow.

Its defined like

```
{
    "type": "tensor",
    "shape": [1,2,3],
    "componentType": "float32"
}
```
The shape does not support variable length tensors (`-1` in TensorFlow).

The serialization is done using an array of the flat form of the tensor, inner first.

E.g. for a tensor of Shape `[2,3]` and a value of `[[1,2,3], [4,5,6]]`
the serialization is `[1,2,3,4,5,6]`. 

### More types

More types will follow, e.g. Lists.

## Implementation

The Scala implementation resides in `ds`. A Golang implementation resides in the `go_shared/ds` project.

## Problems:

- No Random Access Patterns as each element can have a different size.

