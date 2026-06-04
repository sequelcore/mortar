# Mortar Benchmarks

Mortar benchmark code lives in `java/benchmarks` and Rust LSP benchmarks live
under `rust/crates/mortar-lsp`.

Benchmark output is evidence only when the scenario, baseline, raw artifacts,
environment, limitations, and review status are retained together. Local smoke
output proves a harness can run; it does not support public performance claims.

## Java Commands

Compile benchmark sources:

```bash
gradlew.bat :java:benchmarks:compileJava
```

Run the Java benchmark task:

```bash
gradlew.bat :java:benchmarks:jmh
```

Run real PostgreSQL execution profiles:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency
```

Run the current scalar/mutation Java runtime retained-evidence profiles
locally:

```bash
gradlew.bat :java:benchmarks:jmhR23PostR22JavaRuntime
gradlew.bat :java:benchmarks:jmhR23PostR22JavaRuntimeAllocation
gradlew.bat :java:benchmarks:jmhR23PostR22JavaRuntimeLatency
```

## Rust Tooling Commands

Run the retained Rust tooling benchmark target locally:

```bash
cd rust
cargo bench -p sequel-mortar-lsp --bench r23_rust_tooling_lsp
```

Criterion output under `rust/target/criterion` is local tooling output unless
it is retained with commands, corpus notes, environment metadata, limitations,
derived summary, and review notes.

## Retained Evidence

The benchmark evidence package format is the canonical retained artifact path.
It separates evidence families:

- `java-runtime-postgres`
- `rust-tooling-lsp`
- `vscode-editor-latency`

Retained Java runtime artifacts must include raw JMH JSON, exact commands,
commit metadata, clean-worktree state, JDK/Gradle/PostgreSQL/Testcontainers
metadata, dataset notes, limitations, derived summaries, and review notes.

Retained Rust tooling artifacts must include Criterion output, exact commands,
Rust/Cargo metadata, corpus notes, limitations, derived summaries, and review
notes.

Retained VS Code editor-latency artifacts must include trace JSON, test output,
Bun/VS Code metadata, scenario notes, limitations, derived summaries, and
review notes.

R23 retained evidence packages cover Java scalar/runtime mutation scenarios,
Rust tooling/LSP scenarios, and VS Code editor-latency scenarios. The durable
evidence reference is the package structure itself: manifest, commands,
environment metadata, raw artifacts, summary, limitations, and review notes
retained with each evidence family. Public docs should not depend on temporary
CI run links.

R23 closed with optimization no-go and public performance claims blocked. That
decision means the retained artifacts are useful evidence, but they do not
prove broad speed superiority over any named baseline.

## Current Java Scenarios

Representative Java benchmark scenarios include:

- plain JDBC and Mortar simple reads;
- reusable prepared JDBC and Mortar join/page reads;
- generated fixed reads for `findById`;
- scalar `count` and `exists`;
- row-count insert, update, and delete;
- PostgreSQL `RETURNING` fetch and fetchOptional behavior;
- same-SQL non-returning update batches;
- jOOQ and QueryDSL reference rows where the benchmark defines a fair
  comparison boundary.

Controlled JDBC-double benchmarks measure adapter overhead only. They do not
support PostgreSQL, driver, or product performance claims.

## Public Claim Rules

Public performance claims require:

- retained raw artifacts from the benchmark evidence package or an equivalent
  retained evidence package;
- exact commit, commands, environment, dataset/corpus, warmup, measurement,
  forks, and benchmark source links;
- exact baseline naming;
- separate interpretation for Java runtime, Rust tooling, and editor latency;
- published limitations;
- review sign-off for the exact wording.

Allowed current wording:

> Mortar has retained benchmark evidence and disciplined measurement. Public
> performance claims require retained raw artifacts, disclosed methodology,
> exact baselines, limitations, and readiness review.

Blocked wording includes unqualified speed claims against JDBC, PostgreSQL,
jOOQ, QueryDSL, or application workloads without workload-specific retained
evidence.
