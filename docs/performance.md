# Performance Guide

Mortar performance claims must be measured.

## Runtime Path

Normal Spring query execution stays in Java:

- Java DSL builds an immutable query model.
- PostgreSQL renderer produces SQL and parameters.
- JDBC runtime prepares statements and binds parameters.
- Generated JDBC executors can skip render/model work on stable hot paths and
  use direct typed binders/mappers.
- Rust tooling is used for CLI, snapshots, LSP, and reports, not the hot path.

## What To Measure

- SQL rendering cost with JMH.
- JDBC execution with PostgreSQL/Testcontainers.
- Query plan changes through `EXPLAIN`.
- Allocation changes when query construction paths change.

## Existing Gates

```bash
gradlew.bat :java:benchmarks:check
gradlew.bat :java:dialect-postgres:test
gradlew.bat :java:runtime-jdbc:test
```

The benchmark module keeps threshold smoke checks. Any public performance claim
needs a reproducible report using `docs/performance-report-template.md`.

Current internal baseline:

- `docs/benchmarks/baseline-2026-06-01.md`
- `docs/benchmarks/postgres-execution-2026-06-01.md`

Current performance strategy research:

- `docs/research/postgres-performance-strategy.md`

## Rules

- Do not compare Mortar to jOOQ, QueryDSL, Hibernate, or raw JDBC without
  publishing the benchmark source and environment.
- Do not infer database performance from SQL rendering benchmarks.
- Do not treat pretty SQL formatting as the default hot path.
- Prefer simple generated SQL that PostgreSQL can plan predictably.
- Treat maximum handwritten reusable JDBC as a ceiling unless Mortar measures
  otherwise on the same query shape and projection strategy.
