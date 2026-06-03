# Refactor-Safety

Mortar is refactor-safe when supported Java model, relation, or SQL metadata
changes are caught before stale SQL reaches runtime execution.

## Contract

A supported change must produce one of two outcomes:

- generated source, metadata, rendered SQL evidence, and snapshots converge
  after producers and consumers are updated; or
- stale consumers fail deterministically through Java compilation, annotation
  processor diagnostics, snapshot drift, metadata freshness checks, schema
  drift diagnostics, or editor/tooling fail-closed behavior.

Silent stale success is not refactor-safe. A query or editor view must not keep
presenting old generated metadata, old SQL, or old snapshot evidence as current
after source state changes.

## Supported Evidence

Mortar currently has executable evidence for:

- annotated entity field rename and deletion;
- relation deletion;
- relation target or column metadata changes;
- column name metadata changes and resulting SQL drift;
- stale generated metadata, source-map, or snapshot state;
- schema drift diagnostics against database metadata;
- editor/tooling fail-closed behavior when evidence is stale or unsupported.

## Deferred Cases

Mortar does not currently claim refactor-safety coverage for:

- generated optional-filter APIs;
- generated joins, generated projections, generated repositories, generated
  writes, or generated batches;
- self-executing generated query objects;
- arbitrary DSL call-site source maps for every hand-built `QuerySpec`;
- private application migrations;
- binary compatibility promises for generated `Q*` types across user refactors.

## Failure Categories

Stable failure categories:

- Java unresolved generated symbol.
- Annotation processor validation failure with stable Mortar diagnostic code.
- Generated metadata freshness failure.
- SQL snapshot drift.
- Schema drift diagnostic.
- Editor/tooling stale-data fail-closed behavior.

Tests should assert stable semantic evidence: compile pass/fail, Mortar
diagnostic codes, affected generated symbol names, named query SQL, parameters,
parameter types, table/column/join metadata, snapshot keys, metadata format
fields, and schema-drift diagnostics.

Tests should not assert full compiler output, exact diagnostic ordering,
localized compiler text, screenshot-only evidence, private application
migration, or raw generated-file diffs as the only signal.
