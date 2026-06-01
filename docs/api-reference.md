# Public API Reference

This page is the human entry point for Mortar's public Java APIs. Javadoc should
remain the source of exact method signatures.

## Core

Package: `dev.mortar.core`

- `MortarDb`: query entry point.
- `SimpleMortarDb`: default framework-free implementation.
- `TableRef`: SQL table and alias metadata.
- `ColumnRef<T>`: typed column reference and predicate factory.
- `QueryBuilder<T>`: fluent select query builder.
- `QuerySpec`: immutable query model.
- `RenderedQuery`: SQL, parameters, and metadata.
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

Current generated read executors:

- `findAll(renderer)`: selects all mapped columns with explicit empty
  `FindAllParameters`.
- `findById(renderer)`: selects all mapped columns by identifier with
  `FindByIdParameters`.

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
- `MortarExplainPlanAssertions`: PostgreSQL EXPLAIN text assertions.
