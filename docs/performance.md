# Performance

Mortar treats performance as a measured property, not a marketing claim.

Current public wording is limited to this: Mortar has retained benchmark
evidence and disciplined measurement. Public performance claims require retained
raw artifacts, disclosed methodology, exact baselines, limitations, and
readiness review.

Mortar does not currently claim speed superiority over JDBC, PostgreSQL, jOOQ,
QueryDSL, Hibernate, Spring Data, or application-specific persistence code.

## Runtime Path

Normal Spring query execution stays in Java:

- Java DSL or generated read facade creates an immutable query value.
- PostgreSQL renderer produces SQL and parameters.
- JDBC runtime prepares statements, binds parameters, executes, and maps rows.
- Generated fixed reads can pre-render stable SQL and expose direct typed
  parameter and row contracts.

Rust tooling is used for CLI, snapshots, metadata, diagnostics, reports, and
LSP/editor behavior. Rust timing evidence does not support Java runtime or
database performance claims.

## Evidence That Exists

R23 retained evidence exists for three separate families:

- Java runtime PostgreSQL benchmark artifacts for current scalar and mutation
  scenarios.
- Rust tooling/LSP Criterion artifacts.
- VS Code editor-latency trace artifacts.

The retained evidence is available as evidence packages with manifests,
commands, environment metadata, raw artifacts, summaries, limitations, and
review notes. Public docs should refer to the evidence families and retention
requirements, not to temporary CI run links.

The R23 review accepted these artifacts as retained project evidence for the
measured families. R23 did not authorize public performance superiority claims,
optimization, or threshold tightening.

## Evidence Boundaries

Java runtime evidence, Rust tooling evidence, and editor-latency evidence must
stay separate.

Java runtime claims require live PostgreSQL/Testcontainers or equivalent
database execution evidence for the exact query shape, row mapping strategy,
connection behavior, and baseline being named.

Rust Criterion measurements can support Rust tooling and LSP implementation
decisions only. They cannot support claims about JDBC, PostgreSQL execution, or
application runtime throughput.

VS Code traces can support extension-host latency discussions only. They cannot
support claims about Java runtime, database execution, or Rust compiler
throughput.

Local smoke output, copied terminal output, build-directory JSON, and unreviewed
reports are not public evidence.

## Measurement Rules

- Use JMH for JVM/runtime microbenchmarks.
- Use PostgreSQL/Testcontainers for database execution claims.
- Use Criterion for Rust tooling benchmarks.
- Name the exact baseline: ordinary JDBC, reusable prepared JDBC, tuned PgJDBC,
  jOOQ, QueryDSL SQL, or another specific implementation.
- Record commit, commands, JDK/Rust/Bun versions, OS, CPU, PostgreSQL version,
  driver settings, dataset or corpus shape, warmup/measurement/fork settings,
  raw artifacts, derived summaries, limitations, and review notes.
- Keep PgJDBC tuning in benchmark or application configuration; Mortar does not
  change PgJDBC defaults.
- Do not compare rendering-only benchmarks to database execution benchmarks.
- Do not infer application workload performance from isolated microbenchmarks.

Benchmark commands and retained-evidence rules live in
[`benchmarks/README.md`](benchmarks/README.md).
