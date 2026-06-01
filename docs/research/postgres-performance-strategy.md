# PostgreSQL Performance Strategy Research

Date: 2026-06-01

Status: Research note with first generated-executor implementation follow-up.

## Objective

Find a serious path for Mortar to outperform ordinary Java database access
without weakening Mortar's core promise: Java-first, refactor-safe,
SQL-transparent persistence.

## Current Baseline

The first real PostgreSQL baseline showed Mortar close to plain JDBC for an
indexed single-row lookup:

- best plain JDBC path: `plainJdbcFetchOptional`;
- best Mortar path: `mortarPreRenderedJdbcFetchOptional`;
- original gap: small, but plain JDBC still won in that full baseline.

The generated-executor follow-up added `MortarGeneratedQuery<P, T>` and
`MortarPreparedQuery<P, T>`. The processor follow-up now emits a canonical
`findById` generated executor for annotated entities. The latest full
throughput baseline shows generated Mortar list fetch as the top measured path
for this query shape, but optional single-row results still overlap the best
plain/JOOQ bands enough that this remains an internal baseline, not publishable
benchmark evidence.

## Source Findings

### PgJDBC already optimizes prepared statements

PgJDBC uses PostgreSQL's extended protocol for `PreparedStatement` by default.
After `prepareThreshold` executions, the driver can switch to server-side named
prepared statements. The driver documentation lists the key benefits:

- sending a statement handle instead of SQL text;
- binary transfer for supported types;
- server-side execution-plan reuse;
- result metadata reuse.

PgJDBC also has a per-connection prepared statement cache controlled by
`preparedStatementCacheQueries` and `preparedStatementCacheSizeMiB`. This means
Mortar should not try to add a generic pool-level statement cache as a first
move.

Source: https://pgjdbc.github.io/pgjdbc/documentation/server-prepare/
Source: https://jdbc.postgresql.org/documentation/use/?lang=en

### PostgreSQL protocol limits where wins can come from

The extended query protocol separates `Parse`, `Bind`, and `Execute`.
Prepared-statement and portal state can be reused, and binary result formats are
selected during `Bind`.

For Mortar, this means the meaningful performance levers are:

- avoid repeated SQL rendering;
- avoid generic parameter binding dispatch;
- avoid generic row mapping/reflection;
- let PgJDBC use its protocol-level statement and metadata cache;
- optionally provide a specialized pinned-connection path for services that can
  safely reuse an actual `PreparedStatement`.

Source: https://www.postgresql.org/docs/17/protocol-flow.html

### Generic prepared plans are not always faster

PostgreSQL can choose custom or generic plans for prepared statements. Generic
plans save planning time, but can be worse when the ideal plan depends heavily
on parameter values. `plan_cache_mode` can override the default behavior, but
that is a database/query tuning decision, not a Mortar default.

Mortar should expose diagnostics and report plan behavior, not globally force
generic plans.

Source: https://www.postgresql.org/docs/17/runtime-config-query.html

### Pool-level statement caching is the wrong default

HikariCP's guidance treats statement caching at the pool layer as an
anti-pattern because drivers are better positioned to exploit database-specific
features and can share plan IDs more efficiently. Mortar should not build a
generic DataSource-level `PreparedStatement` cache.

Source: https://sources.debian.org/src/hikaricp/2.7.1-2/README.md

### Benchmarks need stronger controls

Recent JVM benchmarking research warns that microbenchmarks can be misleading
even when using JMH if the benchmark produces unrealistic JVM profiles. Mortar
must keep real PostgreSQL benchmarks, allocation profiling, latency profiles,
and multiple JDBC baselines.

Source: https://arxiv.org/abs/2605.23570

### Performance cannot break the safety mission

A 2024 empirical study of Java database-access bugs found SQL query, schema, and
API issues dominate database-access bug categories. Mortar's performance work
must preserve static query/schema/API safety rather than devolving into raw SQL
strings.

Source: https://arxiv.org/abs/2405.15008

## What Can Actually Beat Plain JDBC

There are three different "plain JDBC" baselines. Mortar must name which one it
is trying to beat.

### Baseline A: Ordinary plain JDBC

Shape:

- render or keep SQL text in application code;
- call `connection.prepareStatement(sql)` per operation;
- bind parameters manually;
- map `ResultSet` manually;
- close statement per operation.

Mortar can beat this by removing repeated render/model work, using generated
binders/mappers, and aligning PgJDBC settings.

### Baseline B: Tuned plain JDBC

Shape:

- same code style as baseline A;
- PgJDBC configured with appropriate `prepareThreshold`,
  `preparedStatementCacheQueries`, binary transfer, and extended query mode;
- connection pool configured to avoid pool-level statement caching.

Mortar can possibly match or narrowly beat this when generated executors remove
generic Java-side dispatch.

### Baseline C: Maximum handwritten JDBC

Shape:

- long-lived/pinned connection;
- reusable `PreparedStatement`;
- direct primitive setters;
- direct `ResultSet` getters;
- no abstraction checks.

Mortar should not claim it can always beat this. A generated Mortar executor can
approach it, but the best handwritten code with the same knowledge is the floor.
Mortar's realistic goal is to beat ordinary/tuned application code while
keeping refactor safety and transparency.

## Recommended Architecture

### 1. Generated Query Executor

Add an annotation-processor/codegen output that produces a static executor for
each stable query shape.

Responsibilities:

- hold pre-rendered SQL;
- hold stable parameter metadata;
- bind parameters with direct setter calls;
- map rows with generated direct getter calls;
- expose `fetchList`, `fetchOptional`, and mutation methods;
- expose generated SQL for IDE hover/snapshot tooling.

The first runtime contract exists, and the annotation processor now generates a
canonical primary-key lookup executor. Broader repository method generation
remains the next step. This path removes these runtime costs:

- building or walking `QuerySpec`;
- rendering SQL on hot path;
- looping through `Parameter` objects for simple known-typed binders;
- generic class-comparison binding dispatch;
- reflection-based constructor mapping for generated projections.

### 2. Driver-Aware Configuration Contract

Add documented and testable PgJDBC tuning recommendations:

- `preferQueryMode=extended`;
- keep `binaryTransfer=true`;
- tune `prepareThreshold` per workload, with `-1` only for measured hot paths;
- size `preparedStatementCacheQueries` for known query inventory;
- do not add Hikari/pool-level statement caching;
- use explicit column lists, never `select *`, for prepared statement stability.

Mortar should offer diagnostics, not mutate arbitrary application datasource
URLs silently.

### 3. Optional Pinned Prepared Executor

For advanced users with caller-owned connections or dedicated workers, Mortar
now exposes `MortarJdbcClient.prepare(MortarGeneratedQuery<P, T>)`, returning a
`MortarPreparedQuery<P, T>` that owns a `PreparedStatement` for the lifetime of
a connection scope.

This must be opt-in because:

- `PreparedStatement` is connection-bound;
- connections/statements are not generally safe to share across concurrent
  threads;
- DataSource-backed request flows return connections to pools;
- server-side statement invalidation must be handled.

### 4. Benchmark Matrix Upgrade

Add these benchmark groups before making public performance claims:

- `PlainJdbcOrdinaryBenchmark`;
- `PlainJdbcTunedPgJdbcBenchmark`;
- `PlainJdbcReusableStatementBenchmark`;
- `MortarDynamicBenchmark`;
- `MortarPreRenderedBenchmark`;
- `MortarGeneratedExecutorBenchmark`;
- `MortarPinnedPreparedExecutorBenchmark`.

Each group must include:

- list vs optional single-row;
- one-row indexed lookup;
- small page lookup;
- join projection;
- write batch;
- allocation profile;
- sample-time latency profile.

## Decision

The next serious performance slice after generated `findById` should be broader
annotation-processor generation of repository executors from stable Java query
shapes, plus latency and allocation profiles for generated paths.

Rust should not replace the Java JDBC runtime yet. A Rust sidecar/native
PostgreSQL client could only beat JDBC by becoming a new database driver with
its own pooling, protocol, transaction, TLS, cancellation, error mapping, and
Spring integration story. That is a larger product line, not the next Mortar
optimization.

Use Rust where it already fits Mortar's architecture: compiler/tooling,
metadata validation, SQL snapshots, reports, and future query-plan analysis.

## Success Criteria

Mortar can make a serious performance claim only when:

- generated executor beats ordinary plain JDBC in throughput and allocation;
- generated executor is at least competitive with tuned plain JDBC;
- maximum handwritten reusable-statement JDBC is documented as the ceiling;
- raw JSON, environment, commands, JDK, PostgreSQL, PgJDBC settings, and source
  are published;
- results survive repeated runs and latency/allocation profiles.
