# Mortar Benchmarks

Mortar benchmarks live in `java/benchmarks` and use JMH.

## Commands

Compile benchmark sources:

```bash
gradlew.bat :java:benchmarks:compileJava
```

Run all benchmarks:

```bash
gradlew.bat :java:benchmarks:jmh
```

Run the reproducible baseline profile:

```bash
gradlew.bat :java:benchmarks:jmhBaseline
```

This writes JSON results to `java/benchmarks/build/reports/jmh/baseline.json`.

Run one benchmark group:

```bash
gradlew.bat :java:benchmarks:jmh -PjmhIncludes=JdbcExecutionBenchmark
```

Run allocation profiling:

```bash
gradlew.bat :java:benchmarks:jmhAllocation -PjmhIncludes=PostgresRenderingBenchmark
```

Run the baseline profile with allocation metrics:

```bash
gradlew.bat :java:benchmarks:jmhBaselineAllocation
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/baseline-allocation.json`.

Run real PostgreSQL execution throughput benchmarks:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution
```

This starts PostgreSQL 16 through Testcontainers and writes JSON results to
`java/benchmarks/build/reports/jmh/postgres-execution.json`.

Run real PostgreSQL latency percentile benchmarks:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/postgres-execution-latency.json`.

Run real PostgreSQL allocation benchmarks:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/postgres-execution-allocation.json`.

Run a short harness smoke test:

```bash
gradlew.bat :java:benchmarks:jmh -PjmhIncludes=JdbcExecutionBenchmark -PjmhArgs="-wi 0 -i 1 -f 1 -r 100ms -w 100ms"
```

## Retained CI Artifacts

Public-ready benchmark review needs retained raw JSON, not only copied terminal
output. Use the manual `Benchmarks` GitHub Actions workflow for repeated real
PostgreSQL runs with downloadable artifacts.

R20.1 adds the canonical benchmark-readiness audit in
[`r20-benchmark-readiness.md`](r20-benchmark-readiness.md). When this README and
the R20 audit disagree, the R20 audit is the stricter rule for public claims.

Workflow inputs:

- `jmhIncludes`: JMH include regex. Default:
  `PostgresExecutionBenchmark.*FindById.*`.
- `profile`: `throughput`, `allocation`, `latency`, or `all`.
- `repeatCount`: positive integer repeat count.

The workflow runs PostgreSQL benchmarks on `ubuntu-latest`, confirms Docker is
available, writes JMH JSON under `java/benchmarks/build/reports/jmh`, copies
each repeated run to a numbered JSON file, and uploads the JSON files with 30
day retention.

## Current Scenarios

- `PostgresRenderingBenchmark.renderSelectQuery`: Mortar PostgreSQL rendering overhead.
- `JdbcExecutionBenchmark.plainJdbcFetch`: plain JDBC baseline with controlled JDBC doubles.
- `JdbcExecutionBenchmark.mortarJdbcFetch`: Mortar JDBC fetch path against the same controlled JDBC doubles.
- `PostgresExecutionBenchmark.plainJdbcFetch`: real PostgreSQL select through plain JDBC.
- `PostgresExecutionBenchmark.plainJdbcFetchOptional`: real PostgreSQL at-most-one-row select through plain JDBC.
- `PostgresExecutionBenchmark.plainJdbcReusableStatementFetch`: real PostgreSQL select through a reused plain JDBC `PreparedStatement`.
- `PostgresExecutionBenchmark.plainJdbcReusableStatementFetchOptional`: real PostgreSQL at-most-one-row select through a reused plain JDBC `PreparedStatement`.
- `PostgresExecutionBenchmark.mortarJdbcFetch`: real PostgreSQL select through Mortar with render-per-call execution.
- `PostgresExecutionBenchmark.mortarJdbcFetchOptional`: real PostgreSQL at-most-one-row select through Mortar with render-per-call execution.
- `PostgresExecutionBenchmark.mortarPreRenderedJdbcFetch`: real PostgreSQL select through Mortar with pre-rendered SQL.
- `PostgresExecutionBenchmark.mortarPreRenderedJdbcFetchOptional`: real PostgreSQL at-most-one-row select through Mortar with pre-rendered SQL.
- `PostgresExecutionBenchmark.mortarGeneratedJdbcFetch`: real PostgreSQL select through a generated-style Mortar query with direct binder and projection-index mapper.
- `PostgresExecutionBenchmark.mortarGeneratedJdbcFetchOptional`: real PostgreSQL at-most-one-row select through a generated-style Mortar query.
- `PostgresExecutionBenchmark.mortarPreparedGeneratedJdbcFetch`: real PostgreSQL select through a caller-owned reusable Mortar generated prepared query.
- `PostgresExecutionBenchmark.mortarPreparedGeneratedJdbcFetchOptional`: real PostgreSQL at-most-one-row select through a caller-owned reusable Mortar generated prepared query.
- `PostgresExecutionBenchmark.plainJdbcFindByIdFetch`: real PostgreSQL generated-entity `findById` shape through plain JDBC.
- `PostgresExecutionBenchmark.plainJdbcFindByIdFetchOptional`: real PostgreSQL generated-entity `findById` shape through plain JDBC at-most-one-row mapping.
- `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetch`: real PostgreSQL generated-entity `findById` shape through a reused plain JDBC `PreparedStatement`.
- `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetchOptional`: real PostgreSQL generated-entity `findById` shape through a reused plain JDBC `PreparedStatement` with at-most-one-row mapping.
- `PostgresExecutionBenchmark.plainJdbcJoinPageFetch`: real PostgreSQL joined, ordered, paginated read through a reused plain JDBC `PreparedStatement`.
- `PostgresExecutionBenchmark.mortarJoinPageFetch`: real PostgreSQL joined, ordered, paginated read through the Mortar DSL/JDBC path.
- `PostgresExecutionBenchmark.plainJdbcUpdateBatch`: real PostgreSQL update batch through plain JDBC.
- `PostgresExecutionBenchmark.mortarUpdateBatch`: real PostgreSQL update batch through Mortar mutation rendering and JDBC batch execution.
- `PostgresExecutionBenchmark.plainJdbcTunedReusableFindByIdFetch`: real PostgreSQL generated-entity `findById` shape through a reused plain JDBC `PreparedStatement` on a PgJDBC connection configured with `prepareThreshold=1`, `preparedStatementCacheQueries=256`, and `binaryTransfer=true`.
- `PostgresExecutionBenchmark.mortarTunedProcessorGeneratedFindByIdFetch`: real PostgreSQL processor-generated `findById` through Mortar on the same tuned PgJDBC connection settings.
- `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetch`: real PostgreSQL `findById` through the `QBenchmarkClient` executor emitted by the Mortar annotation processor.
- `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetchOptional`: real PostgreSQL at-most-one-row `findById` through the processor-generated executor.
- `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetch`: real PostgreSQL `findById` through a caller-owned reusable prepared query built from the processor-generated executor.
- `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetchOptional`: real PostgreSQL at-most-one-row `findById` through a caller-owned reusable prepared query built from the processor-generated executor.
- `PostgresExecutionBenchmark.jooqFetch`: real PostgreSQL select through jOOQ.
- `PostgresExecutionBenchmark.jooqFetchOptional`: real PostgreSQL at-most-one-row select through jOOQ.
- `PostgresExecutionBenchmark.querydslFetch`: real PostgreSQL select through QueryDSL SQL.
- `PostgresExecutionBenchmark.querydslFetchOptional`: real PostgreSQL at-most-one-row select through QueryDSL SQL.
- `ReferenceRenderingBenchmark.jooqRenderSelectQuery`: jOOQ rendering reference scenario.
- `ReferenceRenderingBenchmark.querydslRenderSelectQuery`: QueryDSL SQL rendering reference scenario.

The controlled JDBC-double benchmarks measure adapter overhead, not database/network
throughput. Public claims require full report data in
[`performance-report-template.md`](../performance-report-template.md).

The real PostgreSQL benchmarks measure deterministic read and write shapes over
a 1,000-row dataset, using live JDBC connections per JMH trial. They are closer
to execution evidence than controlled JDBC doubles, but they still do not
represent a full application workload, connection pooling, concurrent traffic,
or production database tuning. Tuned PgJDBC scenarios are benchmark-local and do
not change Mortar runtime defaults.
The processor-generated `findById` scenarios use a benchmark-only annotated
entity so the measured Mortar path is emitted by the real annotation processor,
not by hand-written benchmark code.

## Thresholds

`thresholds.json` is a bootstrap CI contract for stable benchmark metadata. Its
initial limits are intentionally loose until Mortar publishes reproducible
baseline data.

## Readiness Rules

Public performance claims require:

- `jmhBaseline` output;
- `jmhBaselineAllocation` output for allocation claims;
- `jmhPostgresExecution` output for real database execution claims;
- `jmhPostgresExecutionLatency` output for p50/p95/p99 latency claims;
- exact baseline naming: ordinary JDBC, tuned PgJDBC, reusable prepared JDBC,
  maximum handwritten JDBC, jOOQ, or QueryDSL SQL;
- JDK, OS, CPU, commit, command, fork/warmup/measurement counts, and benchmark
  source links in a report;
- retained raw JMH JSON artifacts from the manual `Benchmarks` workflow for
  any public throughput, allocation, or latency claim;
- separate interpretation for rendering microbenchmarks and JDBC adapter
  overhead;
- no claims about real database throughput from controlled JDBC-double
  benchmarks;
- no public claim until a benchmark-readiness review signs off against retained
  artifacts.

Follow `docs/performance-report-template.md` for public reports.

Internal baselines:

- `baseline-2026-06-01.md`
- `postgres-execution-2026-06-01.md`
- `performance-report-2026-06-01.md`
