# ADR-0004: Generated JDBC Executor Runtime Contract

Date: 2026-06-01

Status: Accepted

## Context

Mortar's first real PostgreSQL benchmark showed the dynamic JDBC path close to
plain JDBC, but not reliably faster. Research on PgJDBC, PostgreSQL prepared
statements, pool-level caching, and JVM benchmarking showed that credible wins
must remove real hot-path work:

- no repeated SQL rendering for stable query shapes;
- no generic parameter binding loop for generated queries;
- no generic row mapper/reflection for generated projections;
- no pool-level statement cache owned by Mortar;
- optional prepared-statement reuse only when the caller owns the connection.

## Decision

Add a `java/runtime-jdbc` generated-query contract:

- `MortarGeneratedQuery<P, T>` exposes SQL, parameter types, query metadata,
  direct typed binding, and direct row mapping.
- `MortarJdbcClient.fetch(...)` and `fetchOptional(...)` execute generated
  queries without invoking the renderer.
- `MortarJdbcClient.prepare(...)` returns `MortarPreparedQuery<P, T>` only for
  caller-owned connections.
- Generated mappers may use projection column indexes because generated code
  owns the exact select-list order.

The contract stays in `runtime-jdbc` because it exposes JDBC types. `java/core`
remains free of JDBC, Spring, PostgreSQL, and IDE dependencies.

## Consequences

- Generated executors can be benchmarked and optimized without changing the
  core query model.
- SQL logging stays redacted and metadata-aware for generated queries.
- DataSource-backed request flows cannot hold prepared statements accidentally.
- Maximum handwritten reusable JDBC remains a valid ceiling; Mortar can compete
  by generating the same direct work while preserving SQL transparency and
  refactor safety.

## Verification

- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --no-daemon`
- `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`
- `gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon`
