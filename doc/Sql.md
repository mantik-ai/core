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

Supported SQL Commands
----------------------

- `SELECT` with optional `WHERE`
- Simple casts `CAST (x AS int32)`. The type name is the same primitive values in [DataTypes](DataTypes.md)
- Mantik prefers non-nullable types, so the type name of nullable types is like `INT32 NULLABLE`
- Simple calculations (`+`/`-`/`*`/`/`) are possible (e.g. `SELECT x + y AS z`) 
- Null checks are possible `[..] WHERE x IS NOT NULL`
- `AND` and `OR` concatenations are possible
- Constants are possible, e.g. `SELECT x + 1 AS foo`
