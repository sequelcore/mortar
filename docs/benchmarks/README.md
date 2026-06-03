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

Run the R20.4 generated fixed-read throughput profile:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedRead
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/r20.4-generated-fixed-read-throughput.json`.

Run the R20.4 generated fixed-read allocation profile:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadAllocation
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/r20.4-generated-fixed-read-allocation.json`.

Run the R20.4 generated fixed-read latency profile:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadLatency
```

This writes JSON results to
`java/benchmarks/build/reports/jmh/r20.4-generated-fixed-read-latency.json`.

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
  `PostgresExecutionBenchmark\\.(plainJdbcFetch|plainJdbcReusableStatementFetch|plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch|jooqFetch|querydslFetch)$`.
- `profile`: `throughput`, `allocation`, `latency`, or `all`.
- `repeatCount`: integer greater than or equal to `2`.

The workflow runs PostgreSQL benchmarks on `ubuntu-latest`, confirms Docker is
available, writes JMH JSON under `java/benchmarks/build/reports/jmh`, copies
each repeated run into
`java/benchmarks/build/reports/jmh/r20.3/results`, writes a retained
`manifest.json`, `commands.txt`, `summary.md`, `review-notes.md`, and
environment files under `java/benchmarks/build/reports/jmh/r20.3`, then uploads
the bundle with 90 day retention.

## R20.3 PostgreSQL Baseline Matrix

R20.3 is measurement-only. It configures retained artifact generation for the
Java runtime PostgreSQL baseline matrix; it does not authorize public
performance claims or runtime optimization.

Canonical include regex:

```text
PostgresExecutionBenchmark\.(plainJdbcFetch|plainJdbcReusableStatementFetch|plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch|jooqFetch|querydslFetch)$
```

Run the retained CI bundle from the manual `Benchmarks` workflow with:

- `profile`: `all`;
- `repeatCount`: `2` minimum for retained evidence;
- `jmhIncludes`: the canonical regex above.

The workflow runs throughput, allocation, and sample-time latency profiles for
each repeated run. Raw JSON is retained under `results/`; the manifest records
the commit, clean-worktree state, command inputs, JMH profile settings,
environment files, PostgreSQL/Testcontainers/PgJDBC versions, tuned PgJDBC
parameters, dataset shape, matrix row names, and relative artifact paths.

Local smoke command for proving live PostgreSQL JSON generation:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution "-PjmhIncludes=PostgresExecutionBenchmark[.]plainJdbcFetch$" "-PjmhArgs=-wi 0 -i 1 -f 1 -r 100ms -w 100ms -rf json -rff build/reports/jmh/r20.3-smoke.json" --no-daemon
```

The local smoke command intentionally uses one R20.3 method so it is shell-safe
on Windows and quick enough to prove Testcontainers/JMH JSON output. The full
R20.3 matrix is retained through the manual workflow. Local smoke output
remains build output and must not be committed. Use
[`r20-postgres-baseline-manifest-template.json`](r20-postgres-baseline-manifest-template.json)
when creating an explicitly retained local bundle outside GitHub Actions.

R20.3 matrix rows:

- ordinary JDBC: `PostgresExecutionBenchmark.plainJdbcFetch`;
- reusable prepared JDBC: `PostgresExecutionBenchmark.plainJdbcReusableStatementFetch`;
- ordinary JDBC `findById`: `PostgresExecutionBenchmark.plainJdbcFindByIdFetch`;
- reusable prepared JDBC `findById`: `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetch`;
- tuned PgJDBC reusable JDBC: `PostgresExecutionBenchmark.plainJdbcTunedReusableFindByIdFetch`;
- Mortar render-per-call: `PostgresExecutionBenchmark.mortarJdbcFetch`;
- Mortar pre-rendered SQL: `PostgresExecutionBenchmark.mortarPreRenderedJdbcFetch`;
- Mortar processor-generated executor: `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetch`;
- Mortar prepared processor-generated executor: `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetch`;
- Mortar tuned processor-generated executor: `PostgresExecutionBenchmark.mortarTunedProcessorGeneratedFindByIdFetch`;
- jOOQ reference row: `PostgresExecutionBenchmark.jooqFetch`;
- QueryDSL SQL reference row: `PostgresExecutionBenchmark.querydslFetch`.

Optional variants, handwritten generated-style Mortar rows, join/page rows, and
update-batch rows are not R20.3 baseline rows. They remain available benchmark
methods, but their interpretation belongs to R20.4 or R20.5.

## R20.4 Generated Fixed-Read Profiling

R20.4 is measurement-only. It isolates generated `findById` fixed-read overhead
against matched JDBC baselines and does not authorize runtime optimization,
public performance claims, Java public API changes, or runtime behavior
changes.

Canonical include regex:

```text
PostgresExecutionBenchmark\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$
```

R20.4 matrix rows:

- ordinary JDBC `findById`: `PostgresExecutionBenchmark.plainJdbcFindByIdFetch`;
- reusable prepared JDBC `findById`: `PostgresExecutionBenchmark.plainJdbcReusableFindByIdFetch`;
- tuned PgJDBC reusable JDBC `findById`: `PostgresExecutionBenchmark.plainJdbcTunedReusableFindByIdFetch`;
- Mortar processor-generated executor: `PostgresExecutionBenchmark.mortarProcessorGeneratedFindByIdFetch`;
- Mortar prepared processor-generated executor: `PostgresExecutionBenchmark.mortarPreparedProcessorGeneratedFindByIdFetch`;
- Mortar tuned processor-generated executor: `PostgresExecutionBenchmark.mortarTunedProcessorGeneratedFindByIdFetch`.

Local full-profile commands:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedRead --no-daemon
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadLatency --no-daemon
```

Equivalent commands using the generic PostgreSQL tasks:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution "-PjmhIncludes=PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$" --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation "-PjmhIncludes=PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$" --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency "-PjmhIncludes=PostgresExecutionBenchmark\\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$" --no-daemon
```

Local smoke command for proving the R20.4 preset and JSON output:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedRead "-PjmhIncludes=PostgresExecutionBenchmark[.]mortarProcessorGeneratedFindByIdFetch$" "-PjmhArgs=-wi 0 -i 1 -f 1 -r 100ms -w 100ms -rf json -rff build/reports/jmh/r20.4-generated-fixed-read-smoke.json" --no-daemon
```

The smoke command intentionally uses one generated R20.4 method so it is quick
enough to prove the local task, include override, Testcontainers startup, and
JMH JSON writing. The JSON remains build output and must not be committed.

Local retained R20.4 bundles are internal-only unless a later benchmark
readiness review signs off. A local bundle must include raw JSON for
throughput, allocation, and latency, exact commands, commit SHA, clean-worktree
state, date, JDK/Gradle/Docker/PostgreSQL/Testcontainers/PgJDBC versions,
tuned PgJDBC parameters, dataset notes, a derived summary, limitations, and
review notes. Build-directory JSON without that bundle metadata is local
engineering evidence only.

Optional variants, handwritten generated-style Mortar rows, join/page rows,
update-batch rows, jOOQ, QueryDSL SQL, and controlled fake-JDBC rows remain out
of the R20.4 interpretation boundary.

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
