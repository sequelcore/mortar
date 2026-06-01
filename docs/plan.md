# Mortar Development Plan

Date: 2026-06-01
Status: Public planning archive

This document records the current public development plan for Mortar. The
canonical long-term status source is [`roadmap.md`](roadmap.md). When this file
and the roadmap disagree, the roadmap is authoritative.

## Objective

Build Mortar as a Java + Rust query system for Java-first, refactor-safe,
SQL-transparent persistence in Spring applications.

Mortar is not trying to hide SQL. It is designed so application developers can
write normal Java code while still seeing, testing, explaining, and benchmarking
the exact SQL that will run.

## Product Boundary

Mortar is scoped around:

- a Java-first query DSL;
- annotation-processor generated metamodels and common executors;
- PostgreSQL SQL rendering;
- predictable JDBC execution;
- Spring Boot integration;
- SQL snapshots, diagnostics, and editor visibility;
- Rust-based CLI, compiler, LSP, and reporting tooling.

Mortar is intentionally not a full ORM. It does not own aggregate loading,
hidden lazy loading, object identity tracking, or broad database abstraction
until the project has evidence for each added dialect.

## Architecture Plan

Java owns the user-facing application API:

- `java/core`: framework-free query model and DSL contracts;
- `java/processor`: generated metamodels, metadata, and generated executor code;
- `java/dialect-postgres`: PostgreSQL rendering;
- `java/runtime-jdbc`: JDBC execution of rendered/generated query plans;
- `java/spring-boot-starter`: Spring Boot adapter;
- `java/testkit`: SQL and EXPLAIN assertions.

Rust owns tooling and compiler infrastructure:

- `rust/crates/mortar-cli`: command-line tooling;
- `rust/crates/mortar-compiler`: metadata, snapshot, and report foundations;
- `rust/crates/mortar-lsp`: editor-neutral LSP server.

Rust is not part of the default per-query Spring/JDBC runtime path.

## Completed Public Foundation

The following work is complete and represented in the roadmap:

- project foundation, licensing, governance, and CI;
- core query model and Java-first DSL;
- PostgreSQL dialect;
- JDBC runtime;
- Spring Boot starter;
- testkit;
- Rust CLI/compiler tooling;
- SQL snapshots and static diagnostics;
- JMH performance program;
- LSP, VS Code, Neovim guidance, and IntelliJ adapter;
- documentation, examples, release policy, and benchmark reports.

## Active Public-Readiness Work

The current performance report is a public-readiness draft, not publishable
performance evidence. Before Mortar can make public performance claims, the
project must:

- run the manual `Benchmarks` GitHub Actions workflow on a clean commit;
- retain raw JMH JSON artifacts from CI;
- attach release commit metadata to the report;
- record reviewer sign-off against the retained artifacts;
- add at least one broader workload beyond the current indexed lookup shape.

The current engineering evidence supports this narrower statement:

> Mortar's generated prepared JDBC path is already in the same throughput and
> latency band as direct JDBC for the measured lookup shape, with allocation
> behavior close to reusable prepared JDBC and materially lower than the
> measured jOOQ/QueryDSL paths.

It does not support a broad claim that Mortar is universally faster than
maximum handwritten JDBC.

## Verification Matrix

Java gate:

```bash
gradlew.bat check --no-daemon
```

Rust gate:

```bash
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

VS Code extension gate:

```bash
cd editors/vscode
bun run typecheck
bun run test
```

Benchmark report gate:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency --no-daemon
```

Public documentation scrub:

- search public source files for local absolute paths, private workspace roots,
  hostnames, and usernames;
- exclude generated build outputs and dependency caches;
- treat any match as a release blocker until it is replaced with a relative
  path, neutral fixture, or documented placeholder.

## Next Decision Point

After public-readiness cleanup, the next product decision should choose one of:

- publishability work: CI benchmark artifacts, release metadata, and reviewer
  sign-off;
- generated repository executors beyond primary-key lookup;
- broader benchmark workloads: joins, pages, write batches, and tuned PgJDBC;
- editor workflow hardening around SQL hover, EXPLAIN, and diagnostics.

The next slice should update [`roadmap.md`](roadmap.md) in the same change.

## Active Slice: R15 Public API Readiness Hardening

Status: In Progress

Objective: complete the remaining public API readiness hardening work before
any first release tag is considered.

Completed in the current R15 branch:

- R15.1 generated `findAll(renderer)` on `Q*` metamodels.
- R15.2 added `MortarNoParameters` and no-parameter generated query overloads.

Remaining implementation order:

1. R15.3 Spring Boot starter ergonomics.
   Files: `java/spring-boot-starter/src/main/java/dev/mortar/spring/*`,
   `java/spring-boot-starter/src/test/java/dev/mortar/spring/*`,
   `examples/spring-boot-postgres/src/main/resources/application.yml`,
   `docs/spring-boot-postgres-example.md`,
   `docs/spring-boot-compatibility.md`, and `docs/roadmap.md`.
   Verification: `gradlew.bat :java:spring-boot-starter:test --no-daemon` and
   `gradlew.bat :examples:spring-boot-postgres:test --no-daemon`.

2. R15.4 PostgreSQL dialect coverage.
   Files: `java/dialect-postgres/src/test/java/dev/mortar/postgres/*` and
   `docs/roadmap.md`.
   Verification: focused PostgreSQL renderer and Testcontainers tests covering
   joins, pagination, writes, and PostgreSQL-specific predicates.

3. R15.5 Processor diagnostics and generated-source documentation.
   Files: `java/processor/src/main/java/dev/mortar/processor/MortarProcessor.java`,
   `java/processor/src/test/java/dev/mortar/processor/*`,
   `docs/diagnostics.md`, `docs/api-reference.md`, and `docs/roadmap.md`.
   Verification: javac diagnostic tests fail first for invalid public metadata,
   then pass after fail-fast validation and generated executor documentation.

4. R15.6 Public usage guidance.
   Files: `README.md`, `docs/getting-started.md`, new or updated usage docs
   under `docs/`, and `docs/roadmap.md`.
   Verification: documentation scrub plus Java example compilation.

5. R15.7 Broader benchmark scenarios.
   Files: `java/benchmarks/src/main/java/dev/mortar/benchmarks/*`,
   `java/benchmarks/src/test/java/dev/mortar/benchmarks/*`,
   `java/benchmarks/build.gradle.kts`, `.github/workflows/benchmarks.yml`,
   `docs/benchmarks/README.md`, `docs/performance.md`, and
   `docs/roadmap.md`.
   Verification: `gradlew.bat :java:benchmarks:check --no-daemon`.
   Benchmark claims remain draft-only until retained JMH artifacts are reviewed.

6. R15.8 Additional end-to-end examples.
   Files: `settings.gradle.kts`, new modules under `examples/`, example tests,
   README/doc links, and `docs/roadmap.md`.
   Verification: example module `check` tasks and root Java gate.

7. R15.9 Pre-1.0 compatibility policy.
   Files: `docs/release.md`, `CHANGELOG.md`, `README.md`, and
   `docs/roadmap.md`.
   Verification: documentation scrub and release workflow dry-run checks from
   root `check`.

8. R15.10 Public Javadocs.
   Files: public Java source in `java/core`, `java/dialect-postgres`,
   `java/runtime-jdbc`, `java/spring-boot-starter`, `java/processor`, and
   `java/testkit`, plus `docs/api-reference.md` and `docs/roadmap.md`.
   Verification: Java compile with `-Xlint:all -Werror`, public API review, and
   final full gates.

Non-goals:

- no release, tag, Maven Central publication, or merge to `main`;
- no new dialects;
- no ORM aggregate loading, hidden lazy loading, or broad repository abstraction;
- no Spring dependency in `java/core` or `java/runtime-jdbc`;
- no Rust dependency in the default Java/Spring query runtime.

Final verification:

- `gradlew.bat check --no-daemon`;
- from `rust`: `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test`;
- from `editors/vscode`: `bun run typecheck`;
- public path scrub excluding build outputs and caches;
- `git diff --check`.

Risks:

- Testcontainers-dependent tests may skip locally without Docker and still need
  CI evidence.
- Broad benchmark scenarios must not be described as performance claims until
  retained JMH JSON artifacts exist.
- Generated-source Javadocs need to improve public readability without turning
  generated classes into a larger API surface than necessary.
