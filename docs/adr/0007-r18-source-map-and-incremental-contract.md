# ADR-0007: R18 Source-Map And Incremental Contract

Date: 2026-06-02

Status: Accepted

## Problem

R16/R17 generated fixed reads already expose query IDs, generated-source
symbols, ordered parameters, row types, and snapshot keys in
`mortar-metadata-v1`. R18.3 needs a reliable source-map and freshness contract
before editor tooling can trust that metadata. R18.4 also needs a correct
Gradle incremental processor classification, because the processor writes one
shared metadata inventory.

The contract must not expand the generated Java API, add optional filters,
count/exists contracts, writes, repositories, self-executing query objects, ORM
behavior, or editor implementation behavior.

## Decision

Keep `META-INF/mortar/entities.json` as `mortar-metadata-v1`. It remains the
entity/query inventory consumed by Rust tooling and schema drift checks.

Add a sibling processor artifact:

```text
META-INF/mortar/source-map.json
```

The source-map format is `mortar-source-map-v1`. It is keyed by the same fixed
read query IDs as `mortar-metadata-v1` and records:

- the metadata inventory path and freshness fingerprint;
- the generated entity type;
- the generated read namespace;
- the generated fixed-read member, such as `read.findById` or `read.findAll`;
- query name, snapshot key, row type, and ordered parameter metadata;
- a stable source anchor for the originating entity type and fixed-read shape;
- a semantic freshness fingerprint for each query.

R18.3 intentionally records stable source anchors instead of javac line/column
locations. JSR 269 originating elements are file-level dependency hints, and
`Messager` locations are hints that can be approximate. R18 therefore avoids a
line/column contract until a later editor slice has a proven source-position
implementation.

The processor is classified as Gradle `aggregating` while it emits shared
metadata and source-map resources. Preserving `isolating` would require a
larger contract split into per-entity artifacts or a separate aggregation task.
That optimization remains out of scope for R18.3/R18.4.

## Freshness

Freshness fingerprints are deterministic hashes over semantic inventory fields:
query identity, entity table/alias metadata, generated symbol identity, ordered
column metadata, relation metadata, ordered parameter metadata, row type, and
snapshot key. They must not include timestamps, absolute paths, temp
directories, usernames, local build paths, or full generated-source text.

Tooling must fail closed when source-map fingerprints disagree with
`mortar-metadata-v1`, when a query exists only in one artifact, or when the
metadata fingerprint is stale. R18.3 provides parser-level readiness only; VS
Code hover/copy SQL behavior remains R18.5 scope.

## Research Basis

- Gradle incremental annotation processing supports isolating and aggregating
  processors. Isolating processors make decisions per annotated type and must
  provide exactly one originating element per generated file; processors that
  combine unrelated elements into shared outputs fit the aggregating model.
- Gradle incremental builds compare declared inputs and outputs, and stale
  output cleanup is limited. R18 verification must therefore prove stale
  metadata/source-map outputs do not remain apparently valid.
- The Java `Filer` API lets processors provide originating elements as
  dependency-management hints, but those hints are compilation-unit granularity.
- The Java `Messager` API supports element/annotation/value location hints, but
  those hints may be unavailable or approximate.
- ECMA-426 source maps show the useful separation between generated artifacts,
  original sources, mappings, and optional source content. Mortar adopts the
  mapping principle but not the JavaScript VLQ format.
- LSP `Location`, `Range`, hover, and definition concepts inform the shape of
  future editor contracts only. R18.3 does not implement editor behavior.

Primary sources:

- Gradle Java plugin incremental annotation processing:
  https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing
- Gradle incremental build and stale outputs:
  https://docs.gradle.org/current/userguide/incremental_build.html
- Java SE 21 `Filer`:
  https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/annotation/processing/Filer.html
- Java SE `Messager`:
  https://docs.oracle.com/en/java/javase/22/docs/api/java.compiler/javax/annotation/processing/Messager.html
- ECMA-426 source map format:
  https://ecma-international.org/publications-and-standards/standards/ecma-426/
- LSP 3.17 specification:
  https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/

## Alternatives Considered

- Extend `mortar-metadata-v1` with required source-map/freshness fields:
  rejected because it would turn a soft v1 inventory into a cross-tool format
  break.
- Create `mortar-metadata-v2`: rejected for R18.3 because the existing metadata
  inventory remains useful and compatible; only source-map/freshness data needs
  a new artifact.
- Keep the processor `isolating`: rejected while shared resources are emitted.
- Split into per-entity isolating artifacts: valid future optimization, but it
  broadens R18 into artifact discovery and aggregation design.
- Implement VS Code hover/copy SQL now: rejected because R18.3 is a contract
  slice and R18.5 owns editor behavior.

## Consequences

- Existing `mortar-metadata-v1` consumers remain compatible.
- Rust gains parser-level source-map and freshness validation before editor
  behavior depends on the contract.
- Gradle may reprocess more source than an isolating processor would, but the
  classification matches the shared-output behavior.
- R18.4 verification must compare semantic inventories for clean and
  incremental fixture builds, not full generated-file snapshots.
