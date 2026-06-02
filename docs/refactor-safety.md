# Refactor-Safety Contract

Date: 2026-06-02
Status: R18.1 canonical contract

This document defines what Mortar means by `refactor-safe` for the R18
hardening gate. It is a contract for public fixture evidence, not a release,
migration, or generated API expansion.

## Definition

Mortar is refactor-safe when a supported Java model, relation, or SQL metadata
change produces one of two outcomes before runtime query execution:

- the generated `Q*` source, metadata, rendered SQL evidence, and snapshots
  converge after producers and consumers are updated; or
- stale consumers fail deterministically through Java compilation, annotation
  processor diagnostics, snapshot drift, metadata freshness checks, schema
  drift diagnostics, or editor/tooling fail-closed behavior.

Silent stale success is not refactor-safe. A query or editor view must not keep
presenting old generated metadata, old SQL, or old snapshot evidence as current
after the source state changed.

## Supported R18 Cases

R18 uses the R17 service-ticket fixture as evidence. Supported cases are:

- annotated entity field rename:
  `TicketRecord.summary`, `TechnicianRecord.displayName`, and
  `TicketStatusRecord.code`;
- annotated entity field deletion, starting with `TicketRecord.summary`;
- relation deletion:
  `TicketRecord.customer`, `TicketRecord.assignedTechnician`, and
  `TicketRecord.status`;
- relation target or column metadata change, including local-column changes for
  `TicketRecord.status`;
- column name metadata change, including changed SQL for
  `TicketRecord.summary`;
- stale generated metadata, source-map, or snapshot state, handled as
  fail-closed tooling behavior in later R18 source-map/freshness slices;
- schema drift against database metadata, including
  `tickets.status_code`, `tickets.customer_id`,
  `tickets.assigned_technician_id`, and `technicians.region`.

## Deferred Cases

These cases are not supported by R18.1/R18.2 evidence:

- generated optional-filter APIs, generated joins, generated projections,
  generated repositories, generated writes, generated batches, `count`, or
  `exists`;
- self-executing generated query objects or ORM behavior;
- arbitrary DSL call-site source maps or editor hover for every hand-built
  `QuerySpec`;
- broad VS Code or IntelliJ implementation beyond later R18 source-map slices;
- private application migration or private project evidence;
- Gradle incremental correctness claims from clean compile tests alone;
- binary compatibility promises for generated `Q*` types across user
  refactors.

## Failure Categories

R18 assertions use stable semantic categories:

- Java unresolved generated symbol: stale consumers reference a generated field
  or relation that no longer exists after producer source changes.
- Annotation processor validation failure: Mortar emits a stable
  `MORTAR_PROCESSOR_*` diagnostic code for invalid annotated metadata.
- Generated metadata freshness failure: generated metadata, generated source,
  source maps, or current fixture source disagree; tooling must fail closed.
- SQL snapshot drift: rendered SQL, parameters, parameter types, tables,
  columns, joins, or snapshot keys change for a named query.
- Schema drift diagnostic: generated metadata disagrees with database schema
  metadata through CLI/LSP tooling diagnostics.
- Editor/tooling stale-data fail-closed behavior: hover, navigation, or copy
  SQL returns no result or a diagnostic rather than stale SQL.

## Matrix Shape

Each executable case should follow a baseline/fail/recover shape:

| Phase | Meaning | Stable expectation |
| --- | --- | --- |
| Baseline | The unmodified R17-shaped fixture compiles and produces current metadata, SQL, and snapshot evidence. | Compile/test pass and semantic SQL or metadata assertions pass. |
| Fail | A producer entity, field, relation, or metadata value changes while a consumer or snapshot remains stale. | Compile failure, processor diagnostic category, SQL snapshot drift, metadata freshness failure, or schema-drift diagnostic. |
| Recover | The stale consumer or snapshot is updated to the new producer state. | Compile/test pass and metadata, SQL, or snapshot expectations converge. |

Temporary source variants are acceptable when they are created inside tests or
fixtures. Tests must not destructively mutate checked-in R17 source files.

## Assertion Rules

R18 tests and docs must not assert:

- full `javac` output snapshots;
- line-number-dependent compiler failures unless a line contract is explicitly
  documented;
- screenshot-only evidence;
- private application migration as proof;
- Gradle incremental convergence from clean temp-compile tests;
- raw generated-file diffs as the only signal.

Allowed assertions include compile pass/fail, stable Mortar diagnostic codes,
affected generated symbol names, named query SQL/parameter/table/column/join
semantics, snapshot keys, metadata format/version fields, and schema-drift
diagnostic messages.

## Research Basis

R18.1/R18.2 follows current toolchain guidance:

- Gradle incremental annotation processing distinguishes `isolating` and
  `aggregating` processors and requires processor opt-in metadata. Shared
  outputs such as a single metadata inventory are an incremental correctness
  risk and must be proven in R18.4 before making incremental claims.
- `javac` annotation processing runs processors in rounds and compiles
  generated source before normal Java completion, so unresolved generated
  symbols are valid refactor-safety evidence when a stale consumer remains.
- The Java annotation processing API exposes `Messager` for diagnostics and
  `Filer` for generated files/resources; tests should assert stable Mortar
  diagnostic codes rather than complete compiler wording.
- Compile-test harnesses are appropriate for annotation processors, but Mortar
  uses its existing direct `JavaCompiler` test harness to avoid a new test-only
  dependency.
- Generated metadata freshness requires explicit stale-data contracts. R18.2
  classifies it but does not fake it with filesystem hacks; source-map and
  incremental freshness belong to later R18 slices.

References:

- Gradle Java plugin incremental annotation processing:
  https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing
- Gradle incremental build inputs, outputs, and stale outputs:
  https://docs.gradle.org/current/userguide/incremental_build.html
- Oracle `javac` annotation processing:
  https://docs.oracle.com/en/java/javase/11/tools/javac.html#GUID-1B3E7E49-261C-4D7B-8C1A-6A474522B6BB
- Java annotation processing package:
  https://docs.oracle.com/en/java/javase/15/docs/api/java.compiler/javax/annotation/processing/package-summary.html
- Google compile-testing reference:
  https://github.com/google/compile-testing
