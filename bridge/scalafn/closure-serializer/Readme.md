# ScalaFn Closure Serializer

Small helper library which provides serializing of Scala Closures including dependent Java Classes.

The raw serialization is done via Twitter Chill (based upon Kryo).

Class dependencies are serialized as a dynamic generated JAR-File.

The raw serialization is embedded in the JAR file using the path `__root__`.


## Caveats

- All limitations to regular serialization applies (e.g. no serialization of File resources)
- No resources are serialized
- Only classes which are directly referenced can be transported
- Only classes which have a `.class` file present can be transported