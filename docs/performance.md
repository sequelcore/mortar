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
- Joined reads, paginated reads, write batches, and generated-query hot paths.
- Benchmark-local PgJDBC options such as server prepare threshold, statement
  cache size, and binary transfer.
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

R20.3 retained Java runtime PostgreSQL baseline bundles are produced by the
manual `Benchmarks` workflow. The bundle path inside the workflow is
`java/benchmarks/build/reports/jmh/r20.3`, and it contains raw JMH JSON under
`results/` plus `manifest.json`, `commands.txt`, `summary.md`,
`review-notes.md`, and environment files. Local smoke JSON under
`java/benchmarks/build` remains build output and is not public evidence.

R20.4 generated fixed-read profiling uses local JMH presets in
`java:benchmarks`: `jmhR20GeneratedFixedRead`,
`jmhR20GeneratedFixedReadAllocation`, and
`jmhR20GeneratedFixedReadLatency`. These presets isolate ordinary JDBC
`findById`, reusable prepared JDBC `findById`, tuned PgJDBC reusable JDBC
`findById`, Mortar processor-generated `findById`, Mortar prepared
processor-generated `findById`, and Mortar tuned processor-generated
`findById`. Local JSON remains internal engineering evidence unless it is
retained with manifest, commands, environment metadata, limitations, and review
notes.

R20.5 DSL render/execute profiling uses local JMH presets in `java:benchmarks`:
`jmhR20DslShapes`, `jmhR20DslShapesAllocation`, and
`jmhR20DslShapesLatency`. These presets isolate ordinary JDBC simple read,
Mortar DSL render-per-call simple read, Mortar pre-rendered simple read,
reusable prepared JDBC join/page, Mortar DSL join/page, reusable prepared JDBC
update batch, and Mortar DSL update batch. Local JSON remains internal
engineering evidence unless it is retained with manifest, commands,
environment metadata, limitations, dataset notes, and review notes.

R20.6 Rust LSP resolver benchmarking uses Criterion in `rust/crates/mortar-lsp`:

```bash
cd rust
cargo bench -p mortar-lsp --bench r20_lsp_resolver
```

The focused local smoke is:

```bash
cd rust
cargo bench -p mortar-lsp --bench r20_lsp_resolver -- r20_lsp_parser/canonical_generated_read --warm-up-time 0.1 --measurement-time 0.1 --sample-size 10
```

These benchmarks cover parser latency, hover/code-action/definition resolution,
diagnostics, metadata/source-map/snapshot read paths, large-document scans, and
current full-buffer change behavior. Criterion output under
`rust/target/criterion` is internal tooling evidence only. It must not be mixed
into Java runtime or database performance claims.

Current internal baseline:

- `docs/benchmarks/baseline-2026-06-01.md`
- `docs/benchmarks/postgres-execution-2026-06-01.md`
- `docs/benchmarks/r20-benchmark-readiness.md`

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
- Keep driver tuning in benchmark configuration or application configuration;
  Mortar does not change PgJDBC defaults.
- Keep Java runtime and Rust tooling performance reports separate. Rust LSP
  parser/resolver measurements do not support Java runtime claims.
- Do not propose generated fixed-read optimizations from a single local smoke
  or unbundled build-output JSON. R20.4 evidence must be repeated and retained
  before optimization candidates are ranked.
- Do not propose DSL render/execute optimizations from a single local smoke or
  unbundled build-output JSON. R20.5 evidence must be repeated and retained
  before optimization candidates are ranked.
- Do not propose LSP resolver optimizations from a single local Criterion smoke
  or unbundled `rust/target/criterion` output. R20.6 evidence must be repeated,
  retained, scenario-specific, and paired with profiler/allocation evidence
  before tooling optimization candidates are ranked.
