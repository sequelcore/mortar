# Public API Reference

This page is the human entry point for Mortar's public Java APIs. Javadocs
remain the source of exact method signatures.

## Core

Package: `dev.mortar.core`

- `MortarDb`: query entry point.
- `SimpleMortarDb`: default framework-free implementation.
- `TableRef`: SQL table and alias metadata.
- `ColumnRef<T>`: typed column reference and predicate factory.
- `QueryBuilder<T>`: fluent select query builder.
- `QuerySpec`: immutable query model.
- `CountSpec` and `ExistsSpec`: immutable scalar query models.
- `RenderedQuery`: SQL, parameters, parameter types, and metadata.
- `MortarBoundQuery<T>`: immutable named rendered read-query inspection
  contract.
- `MortarBoundScalar<T>`: immutable named rendered scalar inspection contract.
- `InsertSpec`, `UpdateSpec`, and `DeleteSpec`: immutable semantic mutation
  models.
- `MortarBoundMutation`: immutable named rendered row-count mutation contract.
- `MortarReturningMutation<T>`: immutable named rendered mutation contract for
  PostgreSQL `RETURNING` rows.
- `QueryRenderer`: renderer boundary for dialects.

## Processor

Package: `dev.mortar.processor`

- `@MortarEntity`: persistence model table metadata.
- `@MortarId`: identifier marker.
- `@MortarColumn`: column name and nullability metadata.
- `@MortarRelation`: relationship metadata for generated joins.
- `MortarProcessor`: annotation processor that generates `Q*` metamodels and
  fixed read facades for annotated entities.

Generated fixed read facades:

- expose nested row records on the generated `Q*` type;
- accept a `QueryRenderer` so SQL is rendered through the dialect boundary;
- expose `Q*.Read` through `QClient.CLIENT.read(renderer)`;
- return immutable `MortarBoundQuery<FindByIdRow>` and
  `MortarBoundQuery<FindAllRow>` values;
- support `.named("Repository.method")` on the returned bound query;
- include generated-source Javadocs for table metadata, selected columns, and
  SQL shape.

Current generated fixed read facade methods:

- `read(renderer).findById(id)`: selects mapped columns by identifier and binds
  the supplied identifier.
- `read(renderer).findAll()`: explicitly selects all mapped columns.

Generated `Read` facades do not generate execution methods, writes, joins,
optional filters, scalar methods, projections, or repository classes. Scalar
`count` and `exists` are DSL contracts.

## PostgreSQL

Package: `dev.mortar.postgres`

- `PostgresQueryRenderer`: PostgreSQL renderer.
- `PostgresSqlFormat`: compact or pretty rendering mode.
- `PostgresPredicates`: PostgreSQL-specific predicates for arrays, JSONB, and
  full-text search.

## JDBC Runtime

Package: `dev.mortar.jdbc`

- `MortarJdbcClient`: prepared-statement query and mutation executor. It
  supports `QuerySpec`, `RenderedQuery`, `MortarBoundQuery<T>`,
  `MortarBoundScalar<T>`, `MortarBoundMutation`,
  `MortarReturningMutation<T>`, generated queries, optional single-row fetches,
  caller-owned prepared query reuse, and same-SQL non-returning mutation
  batches.
- `MortarGeneratedQuery<P, T>`: generated query contract with SQL, parameter
  types, metadata, direct JDBC binding, and direct row mapping.
- `MortarJdbcBoundQuery<T>`: JDBC row-mapping adapter for a core
  `MortarBoundQuery<T>`.
- `MortarNoParameters`: singleton marker for generated queries without caller
  parameters.
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

- `MortarSqlAssertions`: SQL, parameter, parameter type, name, and metadata
  assertions for rendered and bound values.
- `MortarExplainPlanAssertions`: PostgreSQL EXPLAIN text assertions.
