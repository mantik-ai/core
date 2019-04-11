# Mantik Bridge Specification
=============================

Bridges should abstract the inner implementation of a Machine Learning stack or format.

They are running as docker images.

In general they communicate via HTTP Requests, transporting data usually in [Mantik Bundles](DataTypes.md).

**Note:** The spec is not yet stable and subject of changes.

Startup
-------

Bridges should look for their Mantikfile in the volume `/data/Mantikfile`.

Bridges may have a payload (the zipped `directory`-Element referenced in `Mantikfile`) which will be unpacked 

to `/data/[directoryname]` before startup.

Bridges do not receive any arguments, so a proper `ENTRYPOINT`-Configuration is a must.

Bridges should not run as user root.

Bridges have RW-Access to the `data`-Directory but can't overwrite existing files.


Embedded Http Server
--------------------

Bridges must start a Webserver on Port `8502`.

The following calls must be implemented

- GET `/` must return 200 as soon as the Bridge is ready


The following call should be implemented

- POST `/admin/quit` should stop the server and quit the process.

In general, servers implement 3 different kinds of resources

- Sources: A `GET` call which emits data, is stateless.
- Sinks: A `POST` call which receives a mantik bundle, is stateful.
- Transformer: A `POST` call which receives a mantik bundle and returns a mantik bundle.

Open Points
-----------

- Most HTTP Implementations (and also the Standard) doesn't like servers responding while still receiving
  requests. This makes it necessary for a server implementing a Transformer to cache the whole request although
  the Mantik Bundle format would allow replying row per row.
  A possible solution for that would be web sockets. Also see this [Golang Issue](https://github.com/golang/go/issues/15527)

Content Types
-------------

Bridges must support the following content-type

- `application/x-mantik-bundle` A MessagePack Mantik Bundle / Stream, see [DataTypes.md](DataTypes.md)

They may also support

- `application/x-msgpack`, the same data stream without the header.
- `application/x-mantik-bundle-json` the JSON Representation of the Mantik Bundle
- `application/json` the JSON Representation without the Header.

The latter one is the easiest to test.

Mantik will use `application/x-mantik-bundle` exclusively and always set `Accept` and `Content-Type` Headers.

Algorithm Bridge
----------------

A algorithm bridge must implement the following calls:

- POST `/apply` applies the algorithm on the input mantik bundle and returns the applied mantik bundle.
  The call is a transformer.

It should implement the following call:

- GET `/type` returns the Mantik DS Type in JSON Notation (`application/json`) of the function of the form

    ```
    {
      "input": <Input Type>,
      "output": <Output Type>
    }
    ```

Trainable Algorithm Bridge
--------------------------

A trainable algorithm bridge must implement the following calls:

- POST `/train`. Starts the training process with Mantik Bundle with training data. The Call is a Sink.
  The call may return earlier than training is done.
- GET  `/stats`. Returns statistical information for the training process. This call should 
  either block or return `409` if it's not yet ready.
- GET  `/result`. Returns the payload as ZIP-File for the trained algorithm. This call should
  either block or return `409` if it's not yet ready with training.

It should implement the following calls:

- GET `/training_type` Return the training type as JSON.
- GET `/stat_type` Return the Statistic type as JSON.
- GET `/type` Return the trained algorithm type (like algorithms do), as JSON.

A trainable algorithm bridge may at the same time be an algorithm bridge, but this is not necessary.



Type Safety
-----------

- Bridges can be sure that they receive input and output data in the form like it's presented in their Mantikfile.
- The `type`-Requests are purely for debugging.
