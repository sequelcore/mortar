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

## Active Slice: Generated Common Read Executors

Status: Done

Objective: reduce repository boilerplate by generating common Java-first read
executors from annotated entities, while keeping SQL rendering in dialects and
execution in JDBC runtime.

Scope:

- update `java/processor/src/main/java/dev/mortar/processor/MortarProcessor.java`;
- add processor compile tests in
  `java/processor/src/test/java/dev/mortar/processor/MortarProcessorGenerationTest.java`;
- update public docs that show generated executor usage;
- update `docs/roadmap.md` with verification evidence.

Non-goals:

- no Spring-specific code in generated metamodels;
- no Rust hot-path runtime dependency;
- no broad repository abstraction or ORM behavior;
- no release or Maven Central publication.

Verification:

- focused processor generation test first;
- `gradlew.bat :java:processor:test --no-daemon`;
- root `gradlew.bat check --no-daemon`;
- Rust and VS Code gates if public docs or tooling metadata change.

Result:

- Generated `findAll(renderer)` now exists on `Q*` metamodels with typed
  `FindAllRow`, direct SQL/metadata access, no-op binding, and
  projection-index row mapping.
- `examples/spring-boot-postgres` uses `QClient.CLIENT.findAll(renderer)` in
  `ClientRepository.findAll()`, so the generated API is exercised by a real
  Spring-style repository example.

## Active Slice: Zero-Parameter Generated Query Ergonomics

Status: Done

Objective: remove empty parameter boilerplate from generated read executors so
no-parameter queries execute as `jdbcClient.fetch(query)`.

Scope:

- add a runtime marker for generated queries with no caller parameters;
- add `MortarJdbcClient.fetch(query)` and `fetchOptional(query)` overloads for
  no-parameter generated queries;
- update processor-generated `findAll` to use the no-parameter contract;
- update the Spring example and public docs.

Verification:

- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest.executesGeneratedQueryWithoutParameters --no-daemon`;
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesFindAllExecutorForAnnotatedEntity --no-daemon`.
