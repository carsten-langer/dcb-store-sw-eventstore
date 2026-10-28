# dcb-store-sw-eventstore

A small Scala 3 adapter that implements the generic `DcbStore` API of [dcb-store-scala] on top of third party
[sliceworkz-eventstore].

[dcb-store-scala] itself is small Scala 3 API for **DCB** (Dynamic Consistency Boundary) event stores,
following <https://dcb.events/>.

With this in place, code written against `DcbStore` — projections, decision models, append
conditions — runs unchanged against any sliceworkz storage backend (e.g. in-memory, in-memory-fs,
Postgres).

[dcb-store-scala]: https://github.com/carsten-langer/dcb-store-scala
[sliceworkz-eventstore]: https://sliceworkz.github.io/eventstore/

## Why this is a separate repository

In [dcb-store-scala] `dcb-store` and `dcb-store-inmem` have **no third-party compile dependencies at all**. This adapter
necessarily has: it links `sliceworkz-eventstore-api`, which is **LGPL-3.0** and transitively brings
Jackson and SLF4J (and needs Micrometer at runtime). Keeping the two apart means the clean core can
be depended on — and licensed — without inheriting any of that.

## Goals

* A minimal adapter between  [dcb-store-scala]'s `DcbStore` API and [sliceworkz-eventstore]'s API to persist events
  following the ideas of **DCB** in Scala 3.
* Local publishing of the libraries is considered good-enough for now.

## Non-Goals

* Read models, subscriptions or similar. This is outside the core idea of **DCB**, which focuses on decision models.
  If needed, the mechanisms of [sliceworkz-eventstore] could be used.
* Publishing the libraries to Maven or similar is currently not planned.

## Usage

Currently, there is no Maven-hosted library available, thus in order to use the following `libraryDependencies`,
in _your_ app, you first need to run `sbt publishLocal` on this `dcb-store-sw-eventstore`, see [Building](#building)
below. The artifact is published under group `de.carsten-langer` (the domain seems useful, but is not officially owned by
me).

```scala
libraryDependencies += "de.carsten-langer" %% "dcb-store-sw-eventstore" % "0.1.0-SNAPSHOT"
```

`SwEsDcbStore[DataSuper, JavaDataSuper]` is a trait with four things left to you:

| Member | Purpose |
|---|---|
| `eventStream` | the sliceworkz `EventStream` to read from and append to |
| `javaDataSuperClass` | the Java-sealed root interface of your event records |
| `mapScalaToJava(eventType)(data)` | your Scala event data to the corresponding Java record |
| `mapJavaToScala(data)` | back again |

**Your events must be plain Java** — a `sealed interface` of `record`s, one record per event-type
value you intend to append. This is not a stylistic preference: sliceworkz serializes with
hard-coded Jackson mappers that cannot handle Scala classes, and a Scala `sealed` trait does not
produce a Java-sealed class, which sliceworkz relies on to auto-detect the permitted event types.
The full investigation is in [Readme-Design.md]; read it before trying to use Scala events.

[Readme-Design.md]: Readme-Design.md

## If you are a Play application

This library publishes its `sliceworkz-eventstore-api` dependency **unexcluded**, and Play
version-checks Jackson at runtime. You will probably need to exclude Jackson on your own dependency
on this artifact:

```scala
"de.carsten-langer" %% "dcb-store-sw-eventstore" % version % Compile excludeAll (
  ExclusionRule(organization = "com.fasterxml.jackson.core", name = "jackson-databind"),
  ExclusionRule(organization = "com.fasterxml.jackson.datatype", name = "jackson-datatype-jsr310"),
)
```

Why that is your job rather than the library's — and evidence that as of sliceworkz 0.9.1 the
exclusion may in fact be unnecessary — is in the "Jackson and Play coexistence" section of [Readme-Design.md].

Verify with a running application and a rendered page, not with `compile`: Play checks the Jackson
version at runtime.

## Requirements for consumers

- **Scala 3.8 or newer**, and the same `scalaVersion` as the `dcb-store` build it resolves. Scala 3
  TASTy is only readable by the same or a newer compiler, and here the chain is two hops deep
  (your app → this adapter → `dcb-store`).
- `-language:strictEquality` is expected.
- Java 17+ (developed on Temurin 27 and tested with Temurin 21). The event records need Java 17 for
  `sealed interface` and `record`; Cucumber 8 and Jackson 3 need it as well.
- **Micrometer must be added by you.** sliceworkz needs `io.micrometer:micrometer-core` at runtime but
  declares it optional in its POM, so it does not arrive transitively. This repo's tests use 1.17.2:

  ```scala
  libraryDependencies += "io.micrometer" % "micrometer-core" % "1.17.2"
  ```

## Building

This repo depends on `dcb-store` and, for its tests, on `dcb-store-testkit`. Publishing is **local
only** for now, so publish those first:

```
cd ../dcb-store-scala && sbt "clean; test; publishLocal"
cd ../dcb-store-sw-eventstore
sbt compile
sbt test        # the shared conformance suite: 15 scenarios, 75 steps
sbt publishLocal
```

The test suite is the `DcbStore.feature` conformance spec from `dcb-store-testkit`, run against
sliceworkz' in-memory backend. It must report the **same scenario count** as the `dcb-store-scala`
repo does — a count of zero means the feature file was not found inside the testkit jar, which
Cucumber reports as success rather than as an error.

Only the in-memory backend is exercised here, so no Docker or database is needed.

`-Werror` is on, so any warning fails the build; there is no separate lint step. Doc jars are
deliberately not published, because Scaladoc inherits `-Werror` and the sources contain unresolved
`[[links]]`.

## History

This source code is extracted from a (currently non-public) Play app supporting the (public)
[HealthCareDay](https://github.com/carsten-langer/HealthCareDay), with the history of the extracted files preserved.
