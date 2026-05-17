This is an adapter between the `DcbStore` API and the sliceworkz-eventstore-api library, see
https://sliceworkz.github.io/eventstore/.

With sliceworkz-eventstore, the event root class/interface and the individual event classes must be written in plain
Java and cannot be written in Scala 3. This is for complicated, blocking, and cumbersome reasons.

Blocking reason:
sliceworkz-eventstore uses Jackson to serialize/deserialize Java data to/from JSON. Jackson's default mapping would
not work for Scala. An event interface/class written in Scala is therefore not serializable and yields runtime errors
like:

    Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
    No serializer found for class hcd.sweventstore.HcdEvent and no properties discovered to create BeanSerializer
    (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS)

Jackson supports adding functionality via modules, and a Jackson module for Scala exists,
see https://github.com/FasterXML/jackson-module-scala.
The default way to use the Jackson Scala module is to create a special `ObjectMapper` and register the
`DefaultScalaModule`, then use the `ObjectMapper`. However, for sliceworkz-eventstore, when building the `EventStorage`
(underlying the `EventStore`), I cannot give my own `ObjectMapper`, nor can I explicitly add Jackson modules. Instead,
the individual `EventStorage` implementations have hard-coded solutions.

The `InMemoryFsEventStorageImpl` creates hardcoded its own `ObjectMapper` instance and only registers the
`JavaTimeModule`. There is no way I could register a Scala module to that `ObjectMapper`, and I cannot give my own
`ObjectMapper` instance.

The `InMemoryEventStorageImpl` also creates its own JSON mapper (being a subtype of `ObjectMapper`) and then uses
Jackson's `findAndRegisterModules()` method to search in the class path for other modules. To allow it to find the
Scala module, it must be explicitly put to the class path via sbt libraryDependency, e.g. via

    libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.21.3"

It is a bit unclear if this really works. According to the documentation in
https:github.com/FasterXML/jackson-module-scala?tab=readme-ov-file#javakotlin-users,
explicitly adding the Scala module would have required using
`tools.jackson.module.scala.javadsl.DefaultScalaModule.getInstance()` rather than
`tools.jackson.module.scala.DefaultScalaModule`.
But I verified during debugging that the JSON mapper indeed shows both modules as loaded, the JavaTime and the Scala
module. Thus, the implicit loading of the modules works.

However, I still get the same serialization error, even when the Scala module is loaded. I verified that no serializer
offered by the Scala module is used, so that Jackson defaults back to a standard bean serializer. This serializer tries
to detect class properties via reflection, which is not compatible with Scala (at least Scala 3), thus fails (and yields
the misleading error of "FAIL_ON_EMPTY_BEANS").
Furthermore, https://github.com/FasterXML/jackson-module-scala#classtagextensions suggests that to enable the Scala
module to work with classes with type parameters, as I need them, for Scala 3 the `ClassTagExtensions` need to be mixed
in. This is not possible because I cannot explicitly create the `ObjectMapper` instance and thus not mix in the
`ClassTagExtensions`.

In the `PostgresEventStorageImpl` it is not directly visible, but as this storage implementation also stores data in
JSON, I guess in the end it will also use the `findAndRegisterModules()` to add a Scala module and thus not work.

Even if I could get it to work somehow, there would still be two issues:

- As described above, it could only work for the InMem and Postgres, but not for InMemFs variant, which is due to the
  inconsistency of the upstream implementations. I would have to limit myself to two of three implementations.
- I would not be able to create an `EventStream` from an `EventStore` by giving a single Scala event root class, e.g.
  `eventstore.getEventStream[ScalaEventRootClass](streamId, classOf[ScalaEventRootClass])`.

  This is because sliceworkz-eventstore registers internally per type of "Event Data" a Jackson SerDe instance. When
  given a single event root class, it auto-detects from it all underlying event data classes. For this, to work it
  explicitly checks if the event root `<class>.isSealed` is true.

  It turns out that a Scala "sealed" keyword does _not_ lead to a sealed Java class.
  The Java-sealed feature was only introduced in JDK 17, but Scala had the concept of "sealed" long before that.
  Therefore, "sealed" in Scala, when it was introduced before JDK 17, was only Scala semantics.
  It now seems that even on JKD 17+, the Scala compiler does not translate a Scala sealed class to a Java-sealed
  class. See:
    - https://stackoverflow.com/questions/27937731/overriding-a-sealed-trait-in-scala#comment44284059_27937731
    - https://docs.oracle.com/en/java/javase/17/language/sealed-classes-and-interfaces.html
    - https://www.xn--javaeinfacherklrt-4qb.de/?p=609
    - https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html
    - https://github.com/scala/scala/pull/10105.

  This problem could probably be mitigated by adding instead of the event root class each event class individually, e.g.

      val eventClasses: java.util.Set[Class[?]] = Set(
        classOf[SomeEvent],
        classOf[SomeOtherEvent],
      ).asJava
      eventstore.getEventStream[ScalaEventRootClass](streamId, eventClasses)

To overcome the Jackson serialization problems with Scala instances, I have to resort to only storing and retrieving
plain Java events from the sliceworkz-eventstore API. Therefore, this SwEsDcbStore adapter requires a Java sealed
interface as a root event class, and Scala-to-Java and Java-to-Scala conversions to be explicitly given by a user of
this library.
