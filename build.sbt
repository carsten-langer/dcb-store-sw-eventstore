// This is the single build.sbt file for the whole project.

Global / onChangedBuildSource := ReloadOnSourceChanges

ThisBuild / organization := "de.carsten-langer"

ThisBuild / version := "0.1.0-SNAPSHOT"

// Must match the dcb-store library this adapter is built against: a library built with 3.8.4 can
// only be read by Scala 3.8+. See the dcb-store-scala build for why 3.7 is the hard floor for that code base.
ThisBuild / scalaVersion := "3.8.4"


////////////////////////////////////////
// The dcb-store library this adapter implements
////////////////////////////////////////
// Published from the separate dcb-store-scala repository. Publishing is local only for now, so run
//     sbt clean publishLocal
// in that repository before building here; artifacts then land in the Ivy local repository.
val dcbStoreVersion = "0.1.0-SNAPSHOT"


////////////////////////////////////////
// Common test settings
////////////////////////////////////////
// This is a complicated setup.
// The issue is that key values like "JupiterKeys.junitPlatformVersion.value" cannot be used in normal Scala
// val assignments like "val someVal = ...", but only in sbt key assignments like "someKey := ...".
// To mitigate, a whole sequence of settings is defined here which can then be added to the project,
// similar to what is described in the docs for Task keys, see
// https://www.scala-sbt.org/1.x/docs/Appending-Values.html
// Note that cucumber-scala and scalatest are not listed here: they arrive transitively from the
// dcb-store-testkit artifact, which exposes them as compile dependencies.
val cucumberSbtIntegrationSettingsForTest = Def.settings(
  libraryDependencies ++= Seq(
    // https://github.com/cucumber/cucumber-jvm/tree/main/cucumber-junit-platform-engine
    // Must match the cucumber-core major version that cucumber-scala (via dcb-store-testkit) brings in,
    // see the dcb-store-scala build. A mismatch fails at runtime with an AbstractMethodError.
    "io.cucumber" % "cucumber-junit-platform-engine" % "7.34.4" % Test,
    // https://github.com/junit-team/junit-framework/tree/main/junit-platform-suite
    "org.junit.platform" % "junit-platform-suite" % JupiterKeys.junitPlatformVersion.value % Test,
    // https://github.com/sbt/sbt-jupiter-interface
    "com.github.sbt.junit" % "jupiter-interface" % JupiterKeys.jupiterVersion.value % Test,
  ),
)


////////////////////////////////////////
// The project: a wrapper from the generic DCB store API to the sliceworkz-eventstore DCB store API
////////////////////////////////////////
// https://sliceworkz.github.io/posts/eventstore-quickstart/
// Loading the BOM does not work with sbt (see https://github.com/sbt/sbt/issues/4531), so a simple
// version variable is used.
val swEventStoreVersion = "0.9.1"
lazy val root = (project in file("."))
  .settings(
    name := "dcb-store-sw-eventstore",
    // Scaladoc is invoked with the same scalacOptions as compile, i.e. including -Wall and -Werror.
    // These sources use [[...]] links heavily, so a single dangling link would fail publishLocal.
    // Until the Scaladoc is warning-clean, do not publish a doc jar.
    // Alternative: Compile / doc / scalacOptions -= "-Werror".
    Compile / packageDoc / publishArtifact := false,
    libraryDependencies ++= Seq(
      // The generic DCB store API that this library adapts sliceworkz-eventstore to.
      "de.carsten-langer" %% "dcb-store" % dcbStoreVersion,
      "org.sliceworkz" % "sliceworkz-eventstore-api" % swEventStoreVersion % Compile,
      // The shared DcbStore conformance spec: DcbStoreStepDefinitions plus DcbStore.feature.
      // Also brings cucumber-scala and scalatest transitively.
      "de.carsten-langer" %% "dcb-store-testkit" % dcbStoreVersion % Test,
      // This library is tested with the in-memory variant sliceworkz-eventstore-infra-inmem as an
      // example of the available implementations of sliceworkz-eventstore.
      "org.sliceworkz" % "sliceworkz-eventstore-infra-inmem" % swEventStoreVersion % Test,
      // TODO sliceworkz-eventstore needs some micrometer-core mandatorily.
      // However, it sets it as optional in its dependencies, probably because it does not want to
      // impose any version to the clients. Thus, it is added here explicitly.
      // https://github.com/micrometer-metrics/micrometer
      "io.micrometer" % "micrometer-core" % "1.17.0" % Test,
    ),
    cucumberSbtIntegrationSettingsForTest,
  )


////////////////////////////////////////
// Scala options
////////////////////////////////////////
// Kept identical to the dcb-store-scala build so that "compiles clean there" implies "compiles
// clean here".
ThisBuild / scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-explain", // Explain errors in more detail.
  "-explain-cyclic", // Explain cyclic reference errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:strictEquality", // Enable strict equality (disable canEqualAny).
  "-new-syntax", // Require then and do in control expressions.
  "-source:future",
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Wall", // Enable all warnings.
  // All -Wconf options are stacked to the left. Later options get precedence over earlier ones, if in conflict.
  "-Wconf:any:verbose", // Helpful for debugging. As it is most general, it is the first option.
  "-Werror", // Fail the compilation if there are any warnings. There is no separate lint step.
)


////////////////////////////////////////
// Alias
////////////////////////////////////////
addCommandAlias(
  name = "testInMem",
  value = "Test / testOnly cucumber.dcbstore.swesdcbstore.inmem.SwEsInMemDcbStoreCucumberSpec",
)
