# ADR-0009: R22 Scalar And Mutation Contracts

Date: 2026-06-03

Status: Accepted

## Context

R22 needs the minimum real repository persistence cycle before pre-release
readiness can be evaluated: row reads, scalar reads, create, update, delete,
and bounded batch writes.

Existing Mortar foundations already separate semantic specs, SQL rendering, and
JDBC execution. `java/core` owns framework-free query and mutation models,
`java/dialect-postgres` renders SQL, and `java/runtime-jdbc` executes rendered
plans. R22 must preserve that boundary and must not introduce ORM behavior,
generated repositories, method-name derivation, or self-executing query values.

JDBC and PostgreSQL also separate result shapes:

- scalar reads use result-set execution and return exactly one row/column;
- non-returning DML returns an update count;
- DML with PostgreSQL `RETURNING` returns rows;
- JDBC batch execution returns update counts and is not the R22 path for
  `RETURNING` rows.

## Decision

R22 remains one roadmap gate, but the public contract is split into three
explicit surfaces:

- row reads: existing `QuerySpec` and `MortarBoundQuery<T>`;
- scalar reads: new `CountSpec`, `ExistsSpec`, and `MortarBoundScalar<T>`;
- mutations: existing `InsertSpec`, `UpdateSpec`, and `DeleteSpec` plus new
  `MortarBoundMutation` for update counts and `MortarReturningMutation<T>` for
  PostgreSQL `RETURNING` rows.

`count` and `exists` are DSL-only in R22. They are not generated facade
methods, and processor metadata/source-map contracts remain fixed-read-only.

Mutation specs remain semantic. SQL strings, ordered parameters, parameter
types, metadata, names, result mode, row type, and returning-column order live
on rendered/bound values.

Execution remains only in `MortarJdbcClient`:

- `fetchOne(MortarBoundScalar<T>)` for scalar reads;
- `execute(MortarBoundMutation)` for row-count mutations;
- `fetch(...)` and `fetchOptional(...)` for `MortarReturningMutation<T>`;
- `executeBatch(List<? extends MutationSpec>)` remains bounded to same-SQL,
  non-returning mutation batches.

## Consequences

Accepted:

- SQL remains visible through bound values, snapshots, logs, and testkit
  assertions.
- Spring and Clean Architecture examples use ordinary repository/adapter
  methods and keep Mortar in infrastructure.
- Rust compiler/LSP metadata consumers do not change in R22 because generated
  scalar/write facades are not added.

Rejected:

- generated `count` or `exists` facades;
- generated write namespaces or generated repositories;
- Spring Data-style method-name derivation;
- self-executing scalar or mutation objects;
- dirty checking, managed entity state, lazy loading, identity maps, cascades,
  or aggregate graph loading;
- batch `RETURNING`;
- broad scalar-expression DSLs;
- runtime performance optimization, release, publish, tag, push, PR, migration,
  or announcement work.
