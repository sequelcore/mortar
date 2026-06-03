# ADR-0005: R16 Bound Read Query Contract

Date: 2026-06-01

Status: Accepted

## Problem

R16 needs generated fixed read facades to expose SQL, rendered parameters,
metadata, query identity, and row shape before Mortar adds another generated
facade surface. The contract must preserve SQL transparency without turning the
generated query object into a repository, ORM object, or runtime executor.

The design also needs a minimal processor metadata shape for future tooling
visibility. That metadata must not make editors, Spring, JDBC, or Rust own
query semantics.

## Decision

Add `dev.mortar.core.MortarBoundQuery<T>` as a framework-free inspection
contract for named, rendered read queries. It exposes:

- optional query name;
- the canonical `RenderedQuery`;
- derived SQL text;
- derived bound parameter values and parameter types;
- derived `QueryMetadata`;
- row type metadata.

Add `dev.mortar.jdbc.MortarJdbcBoundQuery<T>` only as a JDBC adapter wrapper
that pairs a core `MortarBoundQuery<T>` with a `RowMapper<T>`. It exposes no
execution methods.

The processor now emits minimal query-id metadata entries for the existing
generated `findAll` and `findById` executor shapes in
`META-INF/mortar/entities.json`. The metadata names the generated symbol,
query kind, parameters, row type, and snapshot key. It does not include rendered
SQL, repository call sites, editor commands, JDBC types, or Spring wiring.

## Alternatives Considered

- Put all bound-query behavior in `java/runtime-jdbc`: rejected because SQL
  visibility, query name, parameters, metadata, and row type are framework-free
  inspection concerns.
- Put JDBC binding and row mapping in `java/core`: rejected because
  `PreparedStatement`, `ResultSet`, and JDBC mapping are adapter concerns.
- Reuse only `MortarGeneratedQuery<P, T>`: rejected because that runtime
  contract is intentionally JDBC-specific and includes bind/map methods.
- Make R16.1 docs-only: rejected because the first generated facade slice needs
  a small public inspection surface and executable metadata parser tests before
  adding the new facade shape.
- Emit full source maps and hover behavior now: rejected because R18 owns
  source-map-backed editor hardening.

## Execution Boundary

Execution remains in `java/runtime-jdbc`. Generated read objects must not add
methods such as `fetch`, `fetchOptional`, `execute`, `save`, `update`,
`delete`, `count`, or `exists`.

Generated query objects expose rendered SQL and metadata; callers still pass
query plans to `MortarJdbcClient` or another adapter to execute them.

## Processor Boundary

The processor does not render SQL. Generated source may accept a
`QueryRenderer` and store a `RenderedQuery`, but SQL text is produced by a
dialect renderer. Processor metadata records query identity and generated
symbols only.

## API Budget

R16 allows only:

- one generated read namespace per entity in R16.2 and later;
- fixed read shapes for `findById` and `findAll`;
- `named(...)`, `rendered()`, `sql()`, `parameterTypes()`, `metadata()`, and
  explicit projection methods when R16.2/R16.3 implement facades;
- no optional-filter overload matrix;
- no generated write namespace;
- no generated repository classes;
- no self-executing generated query methods;
- predictable generated names: `Read`, `Row`, `FindById`, and `FindAll`.

R16.1 enforces the budget through ADR/docs, a processor guard test proving the
new `Read` namespace is not generated yet, and metadata parser tests. R16.2
must add golden tests that enforce the generated API budget when the new
facade shape exists.

## Module Boundaries

- `java/core`: framework-free rendered read-query inspection contract.
- `java/runtime-jdbc`: JDBC row mapping and future execution adapters.
- `java/processor`: generated Java source and tooling metadata; no SQL
  rendering and no execution.
- `java/testkit`: SQL assertions over `RenderedQuery` and
  `MortarBoundQuery<?>`; no JDBC dependency.
- Rust compiler/tooling: parses build metadata; does not run in the Java hot
  path.
- Editors and LSP: consume metadata; do not own query semantics.

## Consequences

- R16.2 can generate a smaller read facade without inventing a cross-layer
  hybrid query object.
- SQL assertions and metadata tooling can target a stable rendered-query
  surface.
- The public API grows by two small types, split by framework boundary.
- The metadata contract is intentionally minimal; R18 must still prove real
  source-map-backed hover/navigation.
- R16 remains a fixed-read ergonomics path, not an ORM or Spring Data-style
  repository layer.

## Architecture Debate Outcome

Architecture review found R16.1 was at risk of becoming too broad.
The accepted correction is to split core inspection from JDBC mapping, avoid a
hybrid `MortarBoundQuery`, keep query-id/source-map metadata minimal, and treat
API-budget enforcement as partly documentary until R16.2 generates the new
facade namespace.
