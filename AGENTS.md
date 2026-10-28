# AGENTS.md

This file provides guidance to AI coding agents (Claude Code, Codex, Copilot, Cursor, ...) when working
with code in this repository. `CLAUDE.md` imports it, so keep this file as the single source.

## What this is

A single-artifact Scala 3 library adapting the generic `DcbStore` API onto
[sliceworkz-eventstore](https://sliceworkz.github.io/eventstore/). Extracted from a (non-public) Play application with history preserved;
that (non-public) Play application is now a consumer.

See `README.md` for the consumer-facing view, and `Readme-Design.md` for the design rationale —
that document is the reason this adapter has the shape it does, and it carries the Jackson/Play analysis.

## The two-repo relationship

This repo consumes two artifacts from the separate `dcb-store-scala` repo, both group `de.carsten-langer`:

- `dcb-store` at Compile — the API being implemented
- `dcb-store-testkit` at Test — the shared `DcbStore.feature` conformance spec plus
  `DcbStoreStepDefinitions`, and transitively cucumber-scala and ScalaTest

The split exists on a dependency-hygiene boundary: `dcb-store` has zero third-party compile
dependencies, whereas this adapter links sliceworkz (LGPL-3.0, plus transitively Jackson 3 and
SLF4J, and Micrometer at runtime). Do not merge them back.

**Publishing is local only.** Build order is `dcb-store-scala` → here → any consumer:

```
cd ../dcb-store-scala && sbt "clean; test; publishLocal"
cd ../dcb-store-sw-eventstore && sbt "clean; test; publishLocal"
```

Keep `scalaVersion` identical across all three builds (3.8.4): TASTy is only readable by the same or
a newer compiler, and the chain is two hops. `dcbStoreVersion` in `build.sbt` pins the upstream
version (currently `0.1.0-SNAPSHOT`).

If an upstream change does not show up here, suspect stale `-SNAPSHOT` resolution first: bump the
upstream `version` to `0.1.0-local.N` and `dcbStoreVersion` to match, rather than fighting the
caches. `reload` matters too, since `update` results are cached per build.

## Commands

```
sbt compile
sbt test           # the shared conformance suite: 15 scenarios, 75 steps
sbt testInMem      # alias for the single suite, by name
sbt publishLocal
```

Only sliceworkz' in-memory backend is exercised, so no Docker or database is needed. (The
in-memory-fs and Postgres backends are exercised in the original (non-public) app, not here.)

Test report: `target/dcbstore-swesdcbstore-inmem-test-report.html` — the Cucumber plugin path is
relative, and the tests are unforked, so it resolves against the build root.

**There is no `sbt` on PATH on the original development machine**; sbt is driven from IntelliJ. From
a shell, either install a launcher or invoke IntelliJ's bundled `sbt-launch.jar` with
`-Dsbt.ivy.home` passed explicitly — `java -jar sbt-launch.jar` does not read `SBT_OPTS`, and
publishing with one invocation while consuming with another produces a "not found" while the
artifact plainly exists in the other `.ivy2`.

## Layout

```
.                                         single sbt project, publishes dcb-store-sw-eventstore
├── src/main/scala/dcbstore/swesdcbstore/SwEsDcbStore.scala   the adapter (the only main source)
├── src/test/scala/cucumber/dcbstore/swesdcbstore/
│   ├── TestEvent.java                    Java sealed interface + 5 records, one per feature event type
│   ├── TestSwEsDcbStore.scala            the adapter wired to TestEvent
│   ├── SwEsDcbStoreStepDefinitions.scala backend-agnostic bridge to DcbStoreStepDefinitions
│   └── inmem/                            the concrete in-memory backend + its runner
├── README.md                             consumer-facing view
├── LICENSE                               Apache 2.0, verbatim
├── Readme-Design.md                      design rationale (Java events, Jackson, Play)
├── NOTICE                                third-party attributions
└── .idea/runConfigurations/              tracked IntelliJ JUnit run config for the in-memory spec
```

The build is a single project at the root. If backend-specific modules are ever added, reintroduce
a `modules/<name>` layout with an aggregator root, mirroring `dcb-store-scala`.

The IntelliJ run config `.idea/runConfigurations/SwEsInMemDcbStoreCucumberSpec.xml` is checked in
and hard-codes the runner's class name, its package and the IntelliJ module name
`dcb-store-sw-eventstore.test`. Renaming or moving the runner, or renaming the sbt project, means
updating it too.

## License

Licensed under Apache 2.0: `LICENSE` holds the verbatim license text, `NOTICE` the copyright line
and third-party attributions, and `ThisBuild / licenses` puts it into the published POM. The root
project copies both files into `META-INF/` of the published binary and sources jar. Keep `NOTICE`
up to date when adding or removing dependencies.

sliceworkz-eventstore stays LGPL-3.0. This adapter only depends on it and does not bundle or
modify it, so the Apache 2.0 license of this repo is unaffected. Do not copy sliceworkz source
into this repo.

## Constraints worth knowing before editing

- **Events must be plain Java.** `TestEvent.java` is a Java `sealed interface` of `record`s, one per
  event-type value in `DcbStore.feature`. sliceworkz serializes with hard-coded Jackson mappers that
  cannot handle Scala classes, and Scala `sealed` does not produce a Java-sealed class — which
  sliceworkz relies on to auto-detect permitted event types. `Readme-Design.md` has the whole investigation.
  This is also why `SwEsDcbStore` demands `javaDataSuperClass`, `mapScalaToJava` and `mapJavaToScala`
  from its user rather than serializing Scala data directly.
- **`TestEvent.java` deliberately lives under `src/test/scala/`.** sbt compiles `.java` found in any
  unmanaged source directory, so this works. Do not "tidy" it into `src/test/java`: that changes the
  mixed-compilation ordering for no benefit.
- **The Cucumber runner must use `@SelectClasspathResource`, not `@SelectPackages`.** The feature file
  is *not in this repo* — it arrives inside the `dcb-store-testkit` jar. Package selection makes JUnit
  scan the classpath for `cucumber/dcbstore/*.feature`, which is unreliable inside jars and fails by
  silently running **zero scenarios** while still reporting success. Always cross-check the scenario
  count against what `dcb-store-scala` reports.
- Cucumber (JVM) 8 needs a **JSON library on the test classpath** for its serialising report plugins
  (this repo's runner configures `html:`), otherwise the run fails with
  "Cucumber needs a JSON library to write reports". It accepts Jackson 3
  (`tools.jackson.core:jackson-databind`) or Jackson 2 (`jackson-databind` + `jackson-datatype-jdk8`).
  This repo declares no JSON library of its own: `sliceworkz-eventstore-api` already brings Jackson 3
  (`tools.jackson.core:jackson-databind`) transitively. If sliceworkz ever drops or changes it, add
  one `% Test` in `cucumberSbtIntegrationSettingsForTest`. The testkit deliberately does not export
  one, so each implementation must supply its own.
- **Java 17+** (Cucumber 8 and Jackson 3 require it). Developed on Temurin 27 and tested with Temurin 21.
- **The Jackson exclusion is deliberately absent.** `sliceworkz-eventstore-api` is published
  unexcluded here; a Play consumer applies the exclusion on its own dependency. See the "Jackson and
  Play coexistence" section of `Readme-Design.md` before changing this, and
  note the measurement recorded there suggesting the exclusion is a no-op with sliceworkz 0.9.1.
- **`Test / fork` is not set**, matching the original configuration in the (non-public) Play
  application. Add it only if class-loading errors actually appear (a sibling sliceworkz module
  there did need it).
- **`-Werror` is the lint step**, and Scaladoc inherits it — hence
  `Compile / packageDoc / publishArtifact := false`. **`-source:future`**, never
  `-rewrite -source:future-migration`, which mutates sources in place.
- **`-language:strictEquality` is on.**
- Micrometer is a mandatory-but-optional-in-POM dependency of sliceworkz, so it is declared
  explicitly at Test scope — and consumers must declare it themselves (README says so). The SLF4J
  "No providers were found" warnings during tests are expected noise: sliceworkz uses SLF4J and no
  provider is on the test classpath.
