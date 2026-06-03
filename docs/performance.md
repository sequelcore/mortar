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

R23.2 retained Java runtime PostgreSQL bundles are produced by the manual
`Benchmarks` workflow. The bundle path inside the workflow is
`java/benchmarks/build/reports/jmh/r23.2-post-r22-java-runtime`, and it
contains raw JMH JSON under `results/` plus `manifest.json`, `commands.txt`,
`summary.md`, `review-notes.md`, and environment files. Local smoke JSON under
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

R23.2 post-R22 Java runtime profiling uses local JMH presets in
`java:benchmarks`: `jmhR23PostR22JavaRuntime`,
`jmhR23PostR22JavaRuntimeAllocation`, and
`jmhR23PostR22JavaRuntimeLatency`. These presets isolate ordinary JDBC and
Mortar rows for `count`, `exists`, insert/update/delete row-count mutations,
representative insert `RETURNING` fetch and fetchOptional behavior, and
same-SQL non-returning update batch writes. Reusable prepared JDBC is used only
for the batch baseline where the lifecycle matches. The retained workflow uses
the same R23.2 matrix and does not include fake JDBC, Rust tooling,
editor-latency rows, jOOQ, QueryDSL, rendering-only rows, optimizations, or
public performance claims.

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

R23.3 Rust tooling evidence uses a retained R23 benchmark target:

```bash
cd rust
cargo bench -p mortar-lsp --bench r23_rust_tooling_lsp
```

The manual `Benchmarks` workflow retains this family under
`rust/target/r23.3-rust-tooling-lsp` with schema
`mortar-r23-rust-tooling-criterion-manifest-v1` and artifact names beginning
`mortar-r23.3-rust-tooling-lsp-`. The R23 target reuses the existing LSP corpus
but emits R23 group names. It remains Rust tooling/LSP evidence only.

R23.4 VS Code editor-latency evidence uses the VS Code smoke test harness with
`MORTAR_VSCODE_LATENCY_TRACE`. The manual `Benchmarks` workflow retains this
family under `editors/vscode/build/r23.4-vscode-editor-latency` with schema
`mortar-r23-vscode-editor-latency-manifest-v1` and artifact names beginning
`mortar-r23.4-vscode-editor-latency-`. This trace boundary is client-visible
extension-host behavior for hover, code actions, definition, diagnostics, copy
SQL, and EXPLAIN command invocation where PostgreSQL is available. It is not
Rust Criterion timing and not Java runtime evidence.

R20.7/R20.8 close as an internal optimization and public-report gate:

- `docs/benchmarks/r20-performance-gate.md` records the evidence-ranked
  optimization table and public-report decision.
- No Java runtime optimization is authorized from R20 evidence. Generated
  binder/mapper, prepared-query lifecycle, renderer reuse, PgJDBC default, and
  threshold changes all require repeated retained Java runtime artifacts and
  review before implementation.
- No Rust tooling optimization is authorized from R20 evidence. Parser caching,
  source-map/snapshot caching, diagnostics scan, and incremental parse strategy
  changes all require repeated retained Criterion/profiler artifacts and review
  before implementation.
- Public performance reporting is no-go until retained artifacts, clean commit
  metadata, exact claim boundaries, limitations, and benchmark-readiness
  sign-off exist.

R23 extends that gate to the post-R22 API surface:

- `docs/benchmarks/r23-retained-performance-evidence.md` defines the scenario
  matrix for generated fixed reads, DSL reads, join/page reads, scalar reads,
  row-count mutations, returning mutations, and same-SQL non-returning batch
  writes.
- R23.2 implements the post-R22 Java runtime scalar/mutation/batch matrix and
  retained artifact workflow.
- R23.3 implements the retained Rust tooling/LSP Criterion workflow.
- R23.4 implements the retained VS Code editor-latency trace workflow.
- R23 keeps Java runtime evidence, Rust tooling evidence, and editor-latency
  traces separate.
- R23 does not authorize optimization or public performance wording until
  retained artifacts, dominant-cost evidence, and benchmark-readiness review
  approve the exact change or claim.
- R23.5 remains pending retained artifact review in
  `docs/benchmarks/r23-performance-gate.md`; R23.6 is not pre-authorized and
  R23.7 before/after review is not applicable unless R23.5 authorizes an
  optimization.

Current internal baseline:

- `docs/benchmarks/baseline-2026-06-01.md`
- `docs/benchmarks/postgres-execution-2026-06-01.md`
- `docs/benchmarks/r20-benchmark-readiness.md`
- `docs/benchmarks/r20-performance-gate.md`
- `docs/benchmarks/r23-benchmark-readiness.md`
- `docs/benchmarks/r23-performance-gate.md`

Current performance strategy research:

- `docs/research/postgres-performance-strategy.md`

Current benchmark planning:

- `docs/benchmarks/r23-retained-performance-evidence.md`

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
- Do not implement R20 optimization candidates or publish R20 performance
  reports without the retained evidence and review recorded as required by
  `docs/benchmarks/r20-performance-gate.md`.
- Do not implement R23 optimization candidates or publish R23 performance
  wording without the retained evidence and review recorded as required by
  `docs/benchmarks/r23-retained-performance-evidence.md`.
