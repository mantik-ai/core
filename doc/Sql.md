SQL Support
===========

Mantik Engine has (very limited) SQL Support for describing DataSet transformations.

Note: You can only transform DataSets of tabular data.

Select Support
--------------

`SELECT` can be used to select only part of a dataset, supporting some filtering.

Suppose `ds` is a DataSet with the following signature:

```
{
    "columns": {
        "x": "int32",
        "y": "string"
    }
}
```

Then you can use `ds.select("SELECT y WHERE x = 100")`.

SQL Operations are executed lazy as all operations. 

Union Support
-------------

DataSets can be appended to each other using `ds.autoUnion`. This will be internally translated into a `UNION` Command.

DataSets will be made fitting (if possible), this menas that columns of the same name and datatype will be presented 
in the target DataSet. If a column doesn't exist in only one of the source datasets, it will be added as a `NULLABLE` column.

Supported SQL Commands
----------------------

- `SELECT` with optional `WHERE`
- Simple casts `CAST (x AS int32)`. The type name is the same primitive values in [DataTypes](DataTypes.md)
- Mantik prefers non-nullable types, so the type name of nullable types is like `INT32 NULLABLE`
- Simple calculations (`+`/`-`/`*`/`/`) are possible (e.g. `SELECT x + y AS z`) 
- Null checks are possible `[..] WHERE x IS NOT NULL`
- `AND` and `OR` concatenations are possible
- Constants are possible, e.g. `SELECT x + 1 AS foo`


How the SQL is implemented
--------------------------
Under the hood the DS Library has a small subset SQL implementation consisting of a Parser, a Compiler and a Runner.

The parser parses SQL Queries. This queries refer to anonymous tabular inputs of the form `$n` (n a Number >= 0). This 
queries may contain `SELECT`, `<left> UNION [all] <right>` and sources `$n` as tabular sources.

The compiler translates them into easily runable programs in a stack based machine. A reference implementation is
available inside the DS Library.

Another implementation of the runner is inside the `select`-Bridge, making it possible to use SQL inside Graph
evaluations and pipelines. The compiled program is serialized into a special `MantikHeader` for this `select`-Bridge.
