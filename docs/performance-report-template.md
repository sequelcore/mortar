# Mortar Performance Report Template

Date: YYYY-MM-DD
Mortar version/commit: fill before publication

## Environment

- OS:
- CPU:
- Memory:
- JDK vendor/version:
- JVM flags:
- PostgreSQL version:
- Docker/Testcontainers version, when applicable:

## Benchmark Method

- Harness: JMH 1.37.
- Warmup iterations:
- Measurement iterations:
- Forks:
- Benchmark command:
- Allocation command:
- Raw result file:

## Scenarios

| Scenario | Baseline | Mortar path | Metric | Result | Notes |
| --- | --- | --- | --- | --- | --- |
| PostgreSQL rendering | N/A | `PostgresQueryRenderer.render` | ops/s | Fill before publication | Rendering only |
| Plain JDBC baseline | Plain JDBC | `JdbcExecutionBenchmark.plainJdbcFetch` | ops/s | Fill before publication | Controlled JDBC doubles |
| Mortar JDBC execution | Plain JDBC | `JdbcExecutionBenchmark.mortarJdbcFetch` | ops/s | Fill before publication | Same JDBC doubles as baseline |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.mortarJdbcFetch` | ops/s | Fill before publication | Render per call |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.mortarJdbcFetchOptional` | ops/s | Fill before publication | Render per call, at-most-one-row |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.mortarPreRenderedJdbcFetch` | ops/s | Fill before publication | Pre-rendered SQL |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.mortarPreRenderedJdbcFetchOptional` | ops/s | Fill before publication | Pre-rendered SQL, at-most-one-row |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.plainJdbcFetch` | ops/s | Fill before publication | PostgreSQL 16 through Testcontainers |
| Real PostgreSQL execution | Plain JDBC | `PostgresExecutionBenchmark.plainJdbcFetchOptional` | ops/s | Fill before publication | PostgreSQL 16 through Testcontainers, at-most-one-row |
| Real PostgreSQL generated findById | Plain JDBC | `PostgresExecutionBenchmark.plainJdbcFindByIdFetch` | ops/s | Fill before publication | Same row shape as processor-generated executor |
| Real PostgreSQL generated findById | Plain JDBC | `PostgresExecutionBenchmark.plainJdbcFindByIdFetchOptional` | ops/s | Fill before publication | Same row shape as processor-generated executor, at-most-one-row |
| Real PostgreSQL generated findById | Reused plain JDBC prepared statement | `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetch` | ops/s | Fill before publication | Maximum handwritten JDBC reference for this shape |
| Real PostgreSQL generated findById | Reused plain JDBC prepared statement | `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetchOptional` | ops/s | Fill before publication | Maximum handwritten JDBC reference for this shape, at-most-one-row |
| Real PostgreSQL generated findById | Processor-generated Mortar executor | `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetch` | ops/s | Fill before publication | Uses `QBenchmarkClient.findById(renderer)` |
| Real PostgreSQL generated findById | Processor-generated Mortar executor | `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetchOptional` | ops/s | Fill before publication | Uses `QBenchmarkClient.findById(renderer)`, at-most-one-row |
| Real PostgreSQL generated findById | Prepared processor-generated Mortar executor | `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetch` | ops/s | Fill before publication | Caller-owned reusable prepared query |
| Real PostgreSQL generated findById | Prepared processor-generated Mortar executor | `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetchOptional` | ops/s | Fill before publication | Caller-owned reusable prepared query, at-most-one-row |
| Real PostgreSQL execution | jOOQ | `PostgresExecutionBenchmark.jooqFetch` | ops/s | Fill before publication | Same schema/query/row materialization |
| Real PostgreSQL execution | jOOQ | `PostgresExecutionBenchmark.jooqFetchOptional` | ops/s | Fill before publication | Same schema/query/row materialization |
| Real PostgreSQL execution | QueryDSL SQL | `PostgresExecutionBenchmark.querydslFetch` | ops/s | Fill before publication | Same schema/query/row materialization |
| Real PostgreSQL execution | QueryDSL SQL | `PostgresExecutionBenchmark.querydslFetchOptional` | ops/s | Fill before publication | Same schema/query/row materialization |
| Reference libraries | jOOQ | `ReferenceRenderingBenchmark.jooqRenderSelectQuery` | ops/s | Fill before publication | Rendering reference |
| Reference libraries | QueryDSL SQL | `ReferenceRenderingBenchmark.querydslRenderSelectQuery` | ops/s | Fill before publication | Rendering reference |

## Interpretation

Record what the numbers prove and what they do not prove. Do not make public
performance claims without raw data, hardware/JDK details, warmup, forks, and
confidence notes.

## Reproducibility

Include the exact command and commit needed to reproduce the report.
