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
- `MortarBoundQuery<T>`: immutable, framework-free named rendered read-query
  inspection contract for SQL, parameters, parameter types, metadata, and row
  type.
- `QueryRenderer`: renderer boundary for dialects.

## Processor

Package: `dev.mortar.processor`

- `@MortarEntity`: persistence model table metadata.
- `@MortarId`: identifier marker.
- `@MortarColumn`: column name and nullability metadata.
- `@MortarRelation`: relationship metadata for generated joins.
- `MortarProcessor`: javac annotation processor that generates `Q*`
  metamodels and common generated JDBC executors for annotated entities.

Generated fixed read facades:

- expose nested row records on the generated `Q*` type;
- accept a `QueryRenderer` so SQL is pre-rendered through the dialect boundary;
- expose `Q*.Read` through `QClient.CLIENT.read(renderer)`;
- return immutable `MortarBoundQuery<FindByIdRow>` and
  `MortarBoundQuery<FindAllRow>` values;
- support copy-style `.named("...")` on the returned bound query;
- include generated-source Javadocs for table metadata, selected columns, and
  generated executor SQL shape.

Current generated fixed read facade methods:

- `read(renderer).findById(id)`: selects all mapped columns by identifier,
  binds the supplied identifier into the rendered query, and can be executed as
  `jdbcClient.fetchOptional(query)`.
- `read(renderer).findAll()`: explicitly selects all mapped columns for
  full-table reads and can be executed as `jdbcClient.fetch(query)`.

The older direct generated executor methods remain available for generated
hot-path execution through `MortarGeneratedQuery`, but R16.2 usage guidance
uses `read(renderer)` as the canonical shorter repository path.

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
  caller-owned prepared query reuse through `prepare(...)`. Supports explicit
  execution of `MortarBoundQuery<T>` and `MortarJdbcBoundQuery<T>` without
  adding execution methods to query objects.
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
