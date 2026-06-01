# Mortar PostgreSQL Performance Report: 2026-06-01

Status: Public-readiness draft. Do not use for public performance claims until
the retained CI benchmark workflow is run on a clean release commit and
reviewed.

## Environment

- OS: Microsoft Windows 10.0.26200.8457.
- CPU: AMD Ryzen 5 5600X 6-Core Processor.
- Memory: 31.9 GiB.
- JDK: Eclipse Temurin OpenJDK 21.0.8.
- Docker: client 29.5.2, server 29.5.2.
- PostgreSQL: 16 through Testcontainers.
- Harness: JMH 1.37.
- Commit: pending release commit.

## Method

The benchmark measures one indexed lookup over a deterministic 1,000-row
`clients` table. Each JMH trial owns one live JDBC connection. Row
materialization is equivalent across plain JDBC, reusable plain JDBC, Mortar,
jOOQ, and QueryDSL SQL.

Commands:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency --no-daemon
```

Profile:

- warmup iterations: 5;
- measurement iterations: 10;
- forks: 3;
- warmup/measurement duration: 1 second each;
- throughput, GC allocation, and sample-time latency profiles.

Local raw JSON:

- `java/benchmarks/build/reports/jmh/postgres-execution.json`
- `java/benchmarks/build/reports/jmh/postgres-execution-allocation.json`
- `java/benchmarks/build/reports/jmh/postgres-execution-latency.json`
- `java/benchmarks/build/reports/jmh/repeated/postgres-execution-run-2.json`
- `java/benchmarks/build/reports/jmh/repeated/postgres-execution-allocation-run-2.json`
- `java/benchmarks/build/reports/jmh/repeated/postgres-execution-latency-run-2.json`

## Results

### Throughput

| Scenario | Mortar | Reference | Result |
| --- | ---: | ---: | --- |
| active+id list | 1,606.551 ops/s ± 25.299 | plain JDBC: 1,572.333 ops/s ± 39.660 | Mortar competitive; intervals overlap. |
| active+id list | 1,606.551 ops/s ± 25.299 | reusable JDBC: 1,609.769 ops/s ± 26.014 | Tie within error. |
| active+id list | 1,606.551 ops/s ± 25.299 | jOOQ: 1,599.695 ops/s ± 31.381 | Tie within error. |
| active+id list | 1,606.551 ops/s ± 25.299 | QueryDSL: 1,556.531 ops/s ± 22.805 | Mortar ahead in this run. |
| processor findById list | 1,600.546 ops/s ± 25.345 | plain JDBC: 1,605.296 ops/s ± 28.140 | Tie within error. |
| processor findById list | 1,600.546 ops/s ± 25.345 | reusable JDBC: 1,617.934 ops/s ± 22.647 | Reusable JDBC slightly ahead in this run. |

### Allocation

| Scenario | Mortar | Reference | Result |
| --- | ---: | ---: | --- |
| active+id list | 829.351 B/op ± 34.900 | plain JDBC: 1,173.404 B/op ± 35.091 | Mortar lower allocation. |
| active+id list | 829.351 B/op ± 34.900 | reusable JDBC: 829.325 B/op ± 34.248 | Tie within error. |
| active+id list | 829.351 B/op ± 34.900 | jOOQ: 10,161.717 B/op ± 43.655 | Mortar materially lower. |
| active+id list | 829.351 B/op ± 34.900 | QueryDSL: 29,515.483 B/op ± 36.918 | Mortar materially lower. |
| processor findById list | 836.672 B/op ± 33.595 | plain JDBC: 1,181.442 B/op ± 35.143 | Mortar lower allocation. |
| processor findById list | 836.672 B/op ± 33.595 | reusable JDBC: 838.895 B/op ± 38.218 | Tie within error. |

### Latency

| Scenario | Mortar | Reference | Result |
| --- | ---: | ---: | --- |
| active+id list mean | 0.618 ms/op ± 0.001 | plain JDBC: 0.613 ms/op ± 0.001 | Plain JDBC slightly lower in this run. |
| active+id list p95 | 0.705 ms/op | reusable JDBC: 0.705 ms/op | Tie. |
| active+id list p99 | 0.817 ms/op | jOOQ: 0.815 ms/op | Tie. |
| processor findById list mean | 0.613 ms/op ± 0.001 | plain JDBC: 0.614 ms/op ± 0.001 | Tie within error. |
| processor findById list p95 | 0.695 ms/op | reusable JDBC: 0.707 ms/op | Mortar lower in this run. |
| processor findById list p99 | 0.803 ms/op | reusable JDBC: 0.819 ms/op | Mortar lower in this run. |

## Interpretation

Mortar's generated prepared path is already in the same throughput and latency
band as direct JDBC for the measured query shape. It does not yet support a
broad public claim that Mortar is faster than maximum handwritten JDBC.

The strongest current result is memory behavior. Prepared generated Mortar
matches reusable prepared JDBC allocation and is materially lower allocation
than ordinary plain JDBC, jOOQ, and QueryDSL SQL for the measured list paths.

This supports the Mortar product thesis: Java-first, refactor-safe query code can
stay SQL-transparent while preserving low-level JDBC performance characteristics.

## Readiness

Internal engineering baseline: ready.

Public performance claim: blocked.

Blocking items:

- run the manual `Benchmarks` GitHub Actions workflow on a clean commit;
- attach retained workflow artifacts to this report;
- add release commit metadata;
- get independent reviewer sign-off against the retained JSON artifacts;
- add at least one broader workload before claiming application-level
  performance.

## Limits

This report is not a benchmark of an application, connection pool, ORM feature
set, concurrent workload, or production PostgreSQL tuning. It measures one
indexed lookup shape under a controlled JMH/Testcontainers setup.
