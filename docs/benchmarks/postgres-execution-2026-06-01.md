# Real PostgreSQL Execution Baseline: 2026-06-01

Status: Internal engineering baseline. Not publishable benchmark evidence.

## Method

Harness: JMH 1.37.

Command:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon
```

Profile settings:

- PostgreSQL 16 through Testcontainers;
- 1,000 deterministic `clients` rows;
- indexed lookup by `active` and `id`;
- one live JDBC connection per JMH trial;
- 5 warmup iterations;
- 10 measurement iterations;
- 3 forks;
- 1 second per warmup/measurement iteration;
- throughput mode.

Raw JSON is generated at
`java/benchmarks/build/reports/jmh/postgres-execution.json` and is not
committed because build outputs are machine-local.

## Results

### Active And Id Lookup

Initial full-profile throughput run:

| Benchmark | Throughput |
| --- | ---: |
| Mortar generated JDBC fetch list | 1,278.584 ops/s ± 60.041 |
| jOOQ fetch optional | 1,260.320 ops/s ± 45.557 |
| jOOQ fetch list | 1,254.216 ops/s ± 40.105 |
| Mortar JDBC fetch list, render per call | 1,223.260 ops/s ± 78.206 |
| Mortar generated JDBC fetch optional | 1,107.225 ops/s ± 76.595 |
| Mortar JDBC fetch optional, render per call | 1,084.043 ops/s ± 80.544 |
| Mortar JDBC fetch list, pre-rendered SQL | 1,061.453 ops/s ± 116.926 |
| Mortar prepared generated JDBC fetch optional | 1,061.441 ops/s ± 63.659 |
| Plain JDBC fetch list, reused prepared statement | 1,038.510 ops/s ± 31.944 |
| Mortar prepared generated JDBC fetch list | 1,036.808 ops/s ± 39.119 |
| Plain JDBC fetch optional | 1,025.613 ops/s ± 24.257 |
| Mortar JDBC fetch optional, pre-rendered SQL | 1,024.527 ops/s ± 41.124 |
| Plain JDBC fetch optional, reused prepared statement | 1,023.859 ops/s ± 30.218 |
| QueryDSL SQL fetch list | 1,019.254 ops/s ± 27.794 |
| Plain JDBC fetch list | 985.714 ops/s ± 38.423 |
| QueryDSL SQL fetch optional | 957.097 ops/s ± 48.024 |

### Processor-Generated FindById Lookup

Follow-up command:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon
```

Profile settings are the same baseline throughput settings above.

| Benchmark | Throughput |
| --- | ---: |
| Mortar prepared processor-generated findById fetch list | 1,682.463 ops/s ± 28.046 |
| Mortar prepared processor-generated findById fetch optional | 1,659.540 ops/s ± 36.046 |
| Mortar processor-generated findById fetch list | 1,648.364 ops/s ± 40.854 |
| Mortar processor-generated findById fetch optional | 1,642.728 ops/s ± 29.989 |
| Plain JDBC findById fetch list | 1,638.574 ops/s ± 30.846 |
| Plain JDBC findById fetch optional | 1,619.011 ops/s ± 27.501 |
| Plain JDBC reusable findById fetch optional | 1,617.938 ops/s ± 24.615 |
| Plain JDBC reusable findById fetch list | 1,601.128 ops/s ± 37.167 |

Allocation follow-up command:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon
```

| Benchmark | Throughput | Allocation |
| --- | ---: | ---: |
| Mortar prepared processor-generated findById fetch list | 1,629.333 ops/s ± 20.083 | 837.660 B/op ± 35.691 |
| Mortar prepared processor-generated findById fetch optional | 1,616.464 ops/s ± 33.646 | 749.906 B/op ± 36.166 |
| Mortar processor-generated findById fetch list | 1,608.461 ops/s ± 36.324 | 1,204.833 B/op ± 33.777 |
| Mortar processor-generated findById fetch optional | 1,597.253 ops/s ± 38.523 | 1,117.767 B/op ± 35.662 |
| Plain JDBC findById fetch list | 1,599.755 ops/s ± 23.386 | 1,182.648 B/op ± 37.639 |
| Plain JDBC findById fetch optional | 1,609.650 ops/s ± 35.895 | 1,092.736 B/op ± 33.576 |
| Plain JDBC reusable findById fetch list | 1,607.115 ops/s ± 27.527 | 839.167 B/op ± 38.658 |
| Plain JDBC reusable findById fetch optional | 1,602.609 ops/s ± 25.585 | 748.715 B/op ± 33.506 |

Latency follow-up command:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon
```

| Benchmark | Mean | p50 | p95 | p99 | p99.9 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Mortar prepared processor-generated findById fetch list | 0.601 ms/op ± 0.001 | 0.588 | 0.688 | 0.774 | 1.049 |
| Mortar prepared processor-generated findById fetch optional | 0.612 ms/op ± 0.001 | 0.597 | 0.713 | 0.830 | 1.308 |
| Mortar processor-generated findById fetch list | 0.615 ms/op ± 0.001 | 0.601 | 0.716 | 0.825 | 1.196 |
| Mortar processor-generated findById fetch optional | 0.623 ms/op ± 0.001 | 0.609 | 0.724 | 0.836 | 1.175 |
| Plain JDBC findById fetch list | 0.626 ms/op ± 0.001 | 0.612 | 0.724 | 0.834 | 1.151 |
| Plain JDBC findById fetch optional | 0.625 ms/op ± 0.001 | 0.611 | 0.726 | 0.826 | 1.107 |
| Plain JDBC reusable findById fetch list | 0.627 ms/op ± 0.001 | 0.612 | 0.725 | 0.839 | 1.564 |
| Plain JDBC reusable findById fetch optional | 0.627 ms/op ± 0.001 | 0.612 | 0.724 | 0.838 | 1.347 |

### Repeated Full-Profile Follow-Up

The follow-up run executed the full PostgreSQL benchmark group again instead of
only the `findById` subset. The local raw JSON artifacts were retained under
`java/benchmarks/build/reports/jmh/repeated` for review, but they remain
machine-local build outputs.

Commands:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency --no-daemon
```

Key throughput results from the repeated full run:

| Benchmark | Throughput |
| --- | ---: |
| Mortar prepared generated active+id list | 1,606.551 ops/s ± 25.299 |
| Plain JDBC active+id list | 1,572.333 ops/s ± 39.660 |
| Plain JDBC reusable active+id list | 1,609.769 ops/s ± 26.014 |
| jOOQ active+id list | 1,599.695 ops/s ± 31.381 |
| QueryDSL active+id list | 1,556.531 ops/s ± 22.805 |
| Mortar prepared processor-generated findById list | 1,600.546 ops/s ± 25.345 |
| Plain JDBC findById list | 1,605.296 ops/s ± 28.140 |
| Plain JDBC reusable findById list | 1,617.934 ops/s ± 22.647 |

Key allocation results from the repeated full run:

| Benchmark | Allocation |
| --- | ---: |
| Mortar prepared generated active+id list | 829.351 B/op ± 34.900 |
| Plain JDBC active+id list | 1,173.404 B/op ± 35.091 |
| Plain JDBC reusable active+id list | 829.325 B/op ± 34.248 |
| jOOQ active+id list | 10,161.717 B/op ± 43.655 |
| QueryDSL active+id list | 29,515.483 B/op ± 36.918 |
| Mortar prepared processor-generated findById list | 836.672 B/op ± 33.595 |
| Plain JDBC findById list | 1,181.442 B/op ± 35.143 |
| Plain JDBC reusable findById list | 838.895 B/op ± 38.218 |

Key latency results from the repeated full run:

| Benchmark | Mean | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Mortar prepared generated active+id list | 0.618 ms/op ± 0.001 | 0.605 | 0.705 | 0.817 |
| Plain JDBC active+id list | 0.613 ms/op ± 0.001 | 0.601 | 0.697 | 0.809 |
| Plain JDBC reusable active+id list | 0.616 ms/op ± 0.001 | 0.603 | 0.705 | 0.814 |
| jOOQ active+id list | 0.629 ms/op ± 0.001 | 0.615 | 0.717 | 0.815 |
| QueryDSL active+id list | 0.630 ms/op ± 0.001 | 0.613 | 0.727 | 0.876 |
| Mortar prepared processor-generated findById list | 0.613 ms/op ± 0.001 | 0.600 | 0.695 | 0.803 |
| Plain JDBC findById list | 0.614 ms/op ± 0.001 | 0.602 | 0.699 | 0.802 |
| Plain JDBC reusable findById list | 0.614 ms/op ± 0.001 | 0.599 | 0.707 | 0.819 |

## Interpretation

- The initial active+id throughput run showed generated Mortar as the strongest
  measured list path for that run, while the repeated full-profile run shows a
  tighter group around direct JDBC.
- The generated path is competitive with ordinary plain JDBC in these runs, but
  confidence intervals overlap in several optional and reusable comparisons.
- The benchmark now separates ordinary JDBC from reusable `PreparedStatement`
  JDBC. Reusable handwritten JDBC remains the ceiling to watch.
- Generated mappers use projection column indexes because generated code owns
  the exact select-list order. That is a real optimization, but maximum
  handwritten JDBC could use the same technique.
- The processor-generated `findById` focused follow-up shows the prepared
  generated Mortar path as the strongest throughput path in that focused
  benchmark group. The repeated full-profile run shows it essentially tied with
  direct JDBC and slightly behind reusable handwritten JDBC in throughput.
- Allocation for prepared processor-generated `findById` is essentially tied
  with reusable plain JDBC in this run. Non-prepared generated execution still
  allocates more than the prepared path because it creates a JDBC prepared
  statement per operation.
- Focused latency sampling for the processor-generated `findById` group shows
  the prepared generated list path with the lowest measured mean, p50, p95, and
  p99 latency in that run. The repeated full-profile latency run shows the same
  paths effectively tied.
- The repeated full-profile run is more conservative than the focused
  `findById` run. It shows Mortar in the same throughput/latency range as
  direct JDBC, not a universal throughput win over reusable handwritten JDBC.
- The strongest repeated result is allocation: prepared generated Mortar is
  essentially tied with reusable prepared JDBC and materially lower allocation
  than ordinary plain JDBC, jOOQ, and QueryDSL for the measured list paths.
- This result is still an internal baseline. Public claims need retained CI
  artifacts from the manual benchmark workflow, release commit metadata, and an
  independent reviewer sign-off.

## Benchmark Readiness Review

Decision: pass for internal engineering baseline; block broad public
performance claims.

Ready:

- benchmark harness, JMH version, commands, dataset shape, database version,
  warmup/measurement/fork counts, and local raw JSON paths are documented;
- controlled JDBC-double adapter-overhead benchmarks are separated from real PostgreSQL
  execution benchmarks;
- full throughput, allocation, and latency profiles now exist for the active+id
  and processor-generated `findById` scenarios;
- the repeated full run prevents over-reading the earlier focused `findById`
  win.

Blocked for public performance claims:

- raw artifacts still need to come from the retained GitHub Actions benchmark
  workflow on a clean commit;
- release commit metadata is not yet attached to this report;
- reviewer sign-off must happen against retained artifacts, not terminal output;
- the measured workload is still one indexed lookup over a 1,000-row dataset.

## Corrections From This Review

- Added `MortarJdbcClient.fetch(RenderedQuery, RowMapper<T>)` so generated or
  cached SQL can execute without invoking the renderer on every call.
- Added `MortarJdbcClient.fetchOptional(...)` for at-most-one-row lookups
  without `ArrayList` and `List.copyOf` materialization.
- Added `MortarGeneratedQuery<P, T>` for pre-rendered generated SQL with direct
  typed binding and direct row mapping.
- Added `MortarPreparedQuery<P, T>` for explicit caller-owned reusable prepared
  generated queries.
- Added `PostgresExecutionBenchmark` with equivalent row materialization across
  plain JDBC, Mortar, jOOQ, and QueryDSL.
- Added Gradle tasks for real PostgreSQL throughput, latency, and allocation
  reports.
- Fixed JMH task profile handling so `-PjmhArgs` can replace the default profile
  for smoke runs without duplicate JMH options.
- Added processor-generated `findById` benchmark scenarios backed by
  `QBenchmarkClient.findById(renderer)`, plus equivalent plain JDBC and reusable
  plain JDBC `findById` baselines.
- Added a repeated full PostgreSQL throughput, allocation, and latency run so
  the focused `findById` result is compared against the full active+id matrix
  before any public-facing interpretation.

## Limitations

- This is one query shape, not a representative application workload.
- It uses one connection per JMH trial and does not measure connection pooling.
- Each JMH fork starts its own PostgreSQL container, so cross-method comparison
  includes container and database startup variance.
- Latency percentile and allocation reports now exist for both benchmark
  groups, but they are still local-run evidence.
- Processor-generated `findById` scenarios now have focused and repeated
  full-profile evidence, but public claims still require retained CI artifacts
  and reviewer sign-off.
- Public claims need release commit, hardware/OS/JDK metadata, raw JSON
  artifacts, reviewer sign-off, and repeated runs.
