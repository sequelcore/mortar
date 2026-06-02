# Public API Reference

This page is the human entry point for Mortar's public Java APIs. Javadoc should
remain the source of exact method signatures.

Before the first release, handwritten public Java types include concise
type-level Javadocs and generated `Q*` sources document SQL table, column,
relation, and generated executor metadata for IDE visibility.

## Core

Package: `dev.mortar.core`

- `MortarDb`: query entry point.
- `SimpleMortarDb`: default framework-free implementation.
- `TableRef`: SQL table and alias metadata.
- `ColumnRef<T>`: typed column reference and predicate factory.
- `QueryBuilder<T>`: fluent select query builder.
- `QuerySpec`: immutable query model.
- `RenderedQuery`: SQL, parameters, and metadata.
- `MortarBoundQuery<T>`: framework-free named rendered read-query inspection
  contract for SQL, parameters, parameter types, metadata, and row type.
- `QueryRenderer`: renderer boundary for dialects.

## Processor

Package: `dev.mortar.processor`

- `@MortarEntity`: persistence model table metadata.
- `@MortarId`: identifier marker.
- `@MortarColumn`: column name and nullability metadata.
- `@MortarRelation`: relationship metadata for generated joins.
- `MortarProcessor`: javac annotation processor that generates `Q*`
  metamodels and common generated JDBC executors for annotated entities.

Generated read executors:

- expose nested parameter and row records on the generated `Q*` type;
- accept a `QueryRenderer` so SQL is pre-rendered through the dialect boundary;
- implement `MortarGeneratedQuery<P, T>` with direct JDBC bind and map methods;
- map selected columns by projection index for the generated hot path.
- include generated-source Javadocs for table metadata, selected columns, and
  generated executor SQL shape.

Current generated read executors:

- `findAll(renderer)`: selects all mapped columns with explicit empty
  `MortarNoParameters` binding and can be executed as `jdbcClient.fetch(query)`.
- `findById(renderer)`: selects all mapped columns by identifier with
  `FindByIdParameters`.

R16.1 does not generate the new R16 fixed read facade namespace yet. The
processor only emits the metadata hooks and keeps the current generated
executor shape until R16.2.

Processor diagnostics fail compilation for invalid entity metadata before a
bad generated query can reach runtime. The stable processor diagnostic codes are
listed in [`diagnostics.md`](diagnostics.md).

## PostgreSQL

Package: `dev.mortar.postgres`

- `PostgresQueryRenderer`: PostgreSQL renderer.
- `PostgresSqlFormat`: compact or pretty rendering mode.
- `PostgresPredicates`: PostgreSQL-specific predicates for arrays, JSONB, and
  full-text search.

## JDBC Runtime

Package: `dev.mortar.jdbc`

- `MortarJdbcClient`: prepared-statement query and mutation executor. Supports
  `QuerySpec` execution and pre-rendered `RenderedQuery` execution for hot
  paths. Supports `fetchOptional(...)` for at-most-one-row lookups. Supports
  generated-query execution through `MortarGeneratedQuery<P, T>` and explicit
  caller-owned prepared query reuse through `prepare(...)`.
- `MortarGeneratedQuery<P, T>`: generated query contract with SQL, parameter
  types, metadata, direct JDBC binding, and direct row mapping.
- `MortarJdbcBoundQuery<T>`: JDBC row-mapping adapter for a core
  `MortarBoundQuery<T>`. It does not execute itself.
- `MortarNoParameters`: singleton marker for generated queries that do not need
  caller-supplied parameters.
- `MortarPreparedQuery<P, T>`: reusable prepared generated query for
  caller-owned connection scopes.
- `RowMapper<T>`: mapper callback for custom row mapping.
- `MortarJdbcLogger`: SQL logging boundary.
- `MortarJdbcException`: execution failure with rendered SQL context.

## Spring Boot

Package: `dev.mortar.spring`

- `MortarAutoConfiguration`: auto-configures core renderer/runtime beans.
- `MortarSpringProperties`: `mortar.*` configuration properties.
- `MortarDiagnosticsEndpoint`: actuator diagnostics endpoint.

## Testkit

Package: `dev.mortar.testkit`

- `MortarSqlAssertions`: SQL, parameter, and metadata assertions.
  Accepts `RenderedQuery` and `MortarBoundQuery<?>`.
- `MortarExplainPlanAssertions`: PostgreSQL EXPLAIN text assertions.
