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

## Post-R15 Roadmap Direction

R15 completed the initial public API readiness hardening path. The next work is
not an application migration. Mortar should first mature its Java authoring
surface, prove that surface against realistic public query fixtures, and harden
tooling before real projects depend on it.

The planned sequence is:

- R16: Java-First Ergonomics And Query Authoring.
- R17: Real-Query Coverage Gate.
- R18: Stability, Refactor Safety And Tooling Hardening.
- R19: Pre-Release Candidate Hardening.

Existing application migration waits until Mortar is much more mature. R17 must
prove R16 ergonomics against a realistic public fixture corpus before any such
migration is considered, and R19 is a go/no-go gate for future beta or controlled
migration pilot planning rather than a release promise.

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

## Next Planned Slice: R16 Java-First Ergonomics And Query Authoring

Status: Planned

R16 should make common query authoring feel better than raw SQL for Java users
while preserving early errors, strong autocomplete, visible SQL, and explicit
runtime behavior.

Planned scope:

- shorter API for common queries;
- generated query facades;
- less repository ceremony;
- better VS Code autocomplete and SQL hover;
- clear diagnostics when fields, classes, or columns change;
- repository and service examples;
- no hidden ORM behavior;
- no loss of SQL transparency.

R16 must not start migration work. It should update [`roadmap.md`](roadmap.md)
in the same change when implementation work begins or when scope changes.

## Future Maturity Gates

R17 is planned as the real-query coverage gate. It should use a public
non-application-specific fixture app and query corpus to cover optional filters,
joins, stable pagination, count and exists queries, DTO projections,
search-style queries, simple writes, batches, and compile-time or refactor
failure cases for renamed or deleted fields. If a capability is not needed by
that realistic query corpus, it should not enter R17.

R18 is planned as stability, refactor-safety, and tooling hardening. It should
cover generator golden tests, source and binary compatibility checks, Gradle
incremental compilation, multi-module cases, metadata-change diagnostics, editor
behavior against real generated contracts, schema drift workflow, and SQL
snapshots as a normal developer workflow.

R19 is planned as pre-release candidate hardening. It should decide whether
Mortar is ready for a serious public beta or release candidate gate, not
automatically release. Its scope includes Windows and Linux CI confidence,
release dry-run, benchmark artifacts from clean commits before claims, upgrade
and migration notes for pre-1.0 changes, security/release/support policy review,
complete CI-running examples, and a go/no-go checklist for a future beta or
later controlled migration pilot.

## Completed Slice: R15 Public API Readiness Hardening

Status: Done

Objective: complete public API readiness hardening before any first release tag
is considered.

Completed scope:

- R15.1 generated `findAll(renderer)` on `Q*` metamodels.
- R15.2 added `MortarNoParameters` and no-parameter generated query overloads.
- R15.3 hardened Spring Boot starter properties, diagnostics, and examples.
- R15.4 expanded PostgreSQL dialect syntax and Testcontainers coverage.
- R15.5 strengthened processor diagnostics and generated-source documentation.
- R15.6 upgraded public usage guidance beyond example snippets.
- R15.7 broadened benchmark scenarios for joins, pages, writes, and tuned
  PgJDBC cases without making unsupported public performance claims.
- R15.8 added a CI-compiling Clean Architecture PostgreSQL example.
- R15.9 finalized the pre-1.0 compatibility and release policy.
- R15.10 completed public Javadocs for handwritten Java APIs and generated
  executor contracts.

Non-goals preserved for release review:

- no release, tag, or Maven Central publication;
- no new dialects;
- no ORM aggregate loading, hidden lazy loading, or broad repository abstraction;
- no Spring dependency in `java/core` or `java/runtime-jdbc`;
- no Rust dependency in the default Java/Spring query runtime.

Final verification record:

- `gradlew.bat check --no-daemon`;
- from `rust`: `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test`;
- from `editors/vscode`: `bun run typecheck`;
- public path scrub excluding build outputs and caches;
- `git diff --check`.

Remaining release-review risks:

- Testcontainers-dependent tests may skip locally without Docker and still need
  CI evidence.
- Broad benchmark scenarios must not be described as performance claims until
  retained JMH JSON artifacts exist.
- Generated-source Javadocs need to improve public readability without turning
  generated classes into a larger API surface than necessary.
