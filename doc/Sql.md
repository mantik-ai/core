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

DataSets will be made fitting (if possible), this means that columns of the same name and datatype will be presented 
in the target DataSet. If a column doesn't exist in only one of the source datasets, it will be added as a `NULLABLE` column.

Join Support
------------

DataSets can be joined with each other using `ds.join`, giving a set of columns to be used for a join. Inner, Left, Right
and Outer joins are supported.

Example:

```
val dataset1 = ...
val dataset2 = ...
val result = dataset1.join(dataset2, Seq("x"), JoinType.Left)
```


Complex joins (and other queries) can be expressed using `DataSet.query` using plain SQL. Input sources can be accessed using `$0`, `$1`, ...

Example:

```
val dataset1 = ...
val dataset2 = ...
val result = DataSet.query(
  "SELECT l.x, l.y, l.z FROM $0 AS l LEFT JOIN $1 AS r ON l.x = r.x
)
```

Supported SQL Commands
----------------------

- `SELECT` with optional `WHERE`
- `UNION` and `UNION ALL`
- `JOIN`, `LEFT JOIN`, `RIGHT JOIN`, `FULL OUTER JOIN`
- Aliasing inside Joins, e.g. `$0 AS r`
- Simple casts `CAST (x AS int32)`. The type name is the same primitive values in [DataTypes](DataTypes.md)
- Mantik prefers non-nullable types, so the type name of nullable types is like `INT32 NULLABLE`
- Simple calculations (`+`/`-`/`*`/`/`) are possible (e.g. `SELECT x + y AS z`) 
- Null checks are possible `[..] WHERE x IS NOT NULL`
- `AND` and `OR` concatenations are possible
- Constants are possible, e.g. `SELECT x + 1 AS foo`
- Accessing Arrays:
  - Values `x[1]` **Note:** SQL Starts counting on index 1.
  - Length: `size(x)`
- Accessing Struct Fields, e.g. `SELECT (p).name`
- DataSet splits: `SPLIT (<query>) AT 0.5, 0.2 WITH SHUFFLE 1` (with a Shuffle-Random seed of `1`)
  **Note:** 
  - Split-Support is only accessiable via `DataSet.split(..)`-Operation
  - A split at `0.5`, `0.2` results in three datasets of the size 50%, 20% and 30%
  - The shuffling should be stable.
  - The shuffling can be omitted (`SPLIT (<query>) AT 0.5`), then no shuffling is done
  - Splitting is a Mantik-Specific SQL Extension
  

Note: not all DataTypes are support by SQL, unsupported are

- Images (except casting)
- Tensors (except casting)  
- Embedded Tables

How the SQL is implemented
--------------------------
Under the hood the DS Library has a small subset SQL implementation consisting of a Parser, a Compiler and a Runner.

The parser parses SQL Queries. This queries refer to anonymous tabular inputs of the form `$n` (n a Number >= 0). This 
queries may contain `SELECT`, `<left> UNION [all] <right>`, `JOIN` and sources `$n` as tabular sources.

The compiler translates them into easily runable programs in a stack based machine. A reference implementation is
available inside the DS Library.

Another implementation of the runner is inside the `select`-Bridge, making it possible to use SQL inside Graph
evaluations and pipelines. The compiled program is serialized into a special `MantikHeader` for this `select`-Bridge.
