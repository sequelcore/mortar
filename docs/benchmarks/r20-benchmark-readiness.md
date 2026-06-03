# R20 Benchmark Readiness And Performance Plan

Date: 2026-06-02
Status: R20.1/R20.2 planning and audit record. No public performance claims.

## Objective

R20 starts Mortar's performance and runtime-efficiency phase with measurement
discipline, not optimization. The goal is to define what Mortar can measure,
which existing benchmark artifacts are trustworthy for internal engineering,
which artifacts are not public-ready, and what evidence must exist before any
public performance claim.

R20 preserves the existing architecture boundary:

- Java owns the runtime query path: DSL, generated query source, PostgreSQL
  rendering, JDBC execution, Spring integration, and JMH/PostgreSQL evidence.
- Rust owns tooling: CLI, compiler metadata, snapshots, reports, and LSP
  parser/resolver performance. Rust benchmark results must not be mixed into
  Java runtime claims.

No runtime optimization, Java public API change, R19 editor semantics change,
release, tag, publication, migration, or public claim is authorized by R20.1 or
R20.2.

## Research Findings

R20 planning used primary or credible sources and the existing Mortar benchmark
material. Findings are intentionally phrased as benchmark-design constraints,
not performance claims.

- JMH is the Java benchmark harness for Mortar runtime and rendering evidence.
  Mortar must keep forks, warmup, measurement iterations, benchmark mode, result
  format, and raw JSON output visible in every report. Source:
  https://github.com/openjdk/jmh
- JMH sample guidance covers dead-code elimination, blackholes, loops, forks,
  per-invocation setup, and profilers. Mortar should use `Blackhole` only where
  returning a value is not practical, avoid hidden setup inside benchmark
  methods, and keep allocation/profile runs separate from throughput
  interpretation. Source:
  https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples
- JVM allocation and CPU claims require profiler evidence, not only throughput
  numbers. JMH GC profiler output is acceptable for allocation slices; external
  profilers such as async-profiler can be used for deeper CPU/heap evidence.
  Sources:
  https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_35_Profilers.java
  and https://github.com/async-profiler/async-profiler
- PgJDBC uses PostgreSQL's extended query protocol for `PreparedStatement` by
  default. Server-side prepare is controlled by `prepareThreshold`; binary
  transfer and per-connection prepared-statement caches are explicit benchmark
  variables, not hidden Mortar defaults. Sources:
  https://jdbc.postgresql.org/documentation/server-prepare/ and
  https://jdbc.postgresql.org/documentation/use/?lang=en
- PostgreSQL prepared statements may use custom or generic plans depending on
  execution history and planner cost. Plan-sensitive claims must record
  `EXPLAIN` evidence and distinguish ordinary execution from
  prepared-statement plan behavior. Sources:
  https://www.postgresql.org/docs/current/sql-prepare.html and
  https://www.postgresql.org/docs/current/sql-explain.html
- PostgreSQL extended query flow separates parse, bind, and execute. Mortar
  benchmarks must separate SQL rendering cost from database prepare/bind/execute
  cost. Source: https://www.postgresql.org/docs/current/protocol-flow.html
- HikariCP treats pool-level prepared-statement caching as an anti-pattern
  because driver-level caches can use database-specific protocol features.
  Mortar must not claim a pool-cache win, and pool behavior is outside the
  current runtime benchmark matrix unless a later slice adds a dedicated pool
  workload. Source: https://github.com/brettwooldridge/HikariCP
- jOOQ and QueryDSL comparisons are easy to misread. Mortar can compare against
  them only when statement lifecycle, bind-vs-inline behavior, SQL shape, fetch
  mode, row materialization target, and type mapping are documented. Sources:
  https://www.jooq.org/doc/latest/manual/sql-execution/performance-considerations/,
  https://www.jooq.org/doc/latest/manual/sql-execution/reusing-statements/,
  https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/custom-settings/settings-statement-type/,
  https://querydsl.com/static/querydsl/latest/reference/html_single/, and
  https://querydsl.com/static/querydsl/5.0.0/apidocs/com/querydsl/sql/SQLQuery.html
- Rust Criterion is the preferred candidate for future LSP/compiler
  microbenchmarks because it supports warmup, repeated measurement, comparison
  output, profiling mode, and custom measurements. CI wall-clock results are
  noisy and must be treated as coarse signal unless retained artifacts and
  repeated runs support the interpretation. Sources:
  https://bheisler.github.io/criterion.rs/book/index.html,
  https://bheisler.github.io/criterion.rs/book/analysis.html,
  https://bheisler.github.io/criterion.rs/book/faq.html,
  https://bheisler.github.io/criterion.rs/book/user_guide/profiling.html, and
  https://bheisler.github.io/criterion.rs/book/user_guide/custom_measurements.html
- LSP/parser benchmarking should include batch and incremental behavior, not
  parser microbenchmarks alone. rust-analyzer's public performance tooling
  guidance supports separating batch analysis from incremental paths. Sources:
  https://rust-analyzer.github.io/book/contributing/ and
  https://rust-analyzer.github.io/book/contributing/guide.html
- Tree-sitter performance work must measure full parse, incremental reparse with
  an old tree, and query/match behavior separately. Relevant controls include
  old-tree reuse, included ranges, parse callbacks, query byte ranges, and match
  limits. Sources:
  https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html,
  https://docs.rs/tree-sitter/latest/tree_sitter/struct.Parser.html,
  https://docs.rs/tree-sitter/latest/tree_sitter/struct.ParseOptions.html, and
  https://docs.rs/tree-sitter/latest/tree_sitter/struct.QueryCursor.html
- Public reporting should use repeatability, reproducibility, and replicability
  discipline: raw artifacts, exact config, commands, hardware, JDK, driver,
  database version, commit, and enough detail for independent reruns. Sources:
  https://www.acm.org/publications/policies/artifact-review-and-badging-current
  and https://www.spec.org/cpu2026/docs/runrules.html

## Xhigh Debate Outcome

The R20 architecture/performance debate concluded:

- First measure the existing PostgreSQL runtime matrix on a clean commit with
  retained artifacts: ordinary plain JDBC, tuned PgJDBC, reusable prepared JDBC,
  Mortar render-per-call, Mortar pre-rendered, processor-generated Mortar, and
  prepared processor-generated Mortar.
- Capture throughput, allocation, and sample-time latency before changing code.
- Measure join/page and update-batch scenarios after the fixed-read baseline is
  stable, so lookup-specific results are not generalized too early.
- Measure Rust LSP parser/resolver/diagnostic latency and allocation separately
  from Java runtime metrics.
- Mortar should target near-zero overhead relative to tuned or reusable JDBC.
  It may plausibly beat ordinary application-style JDBC on generated stable
  paths, but an unqualified "faster than JDBC" claim is not credible.
- Rust helps with compiler, report, snapshot, and LSP tooling. Rust does not
  help per-query JDBC execution unless Mortar starts a separate ADR for a native
  database-driver product line.
- Optimizations must wait until evidence isolates a dominant cost. R20.1 and
  R20.2 authorize measurement design only.

The R20.3 artifact-readiness debate concluded:

- R20.3 must use retained artifact bundles, not terminal output or unbundled
  build-directory JSON, as the evidence unit.
- The baseline matrix is limited to live PostgreSQL fixed-read rows: ordinary
  JDBC, reusable prepared JDBC, tuned PgJDBC reusable JDBC, Mortar
  render-per-call, Mortar pre-rendered SQL, Mortar processor-generated
  executor, Mortar prepared processor-generated executor, jOOQ reference, and
  QueryDSL SQL reference.
- jOOQ and QueryDSL remain reference-library rows. They are included in the
  retained matrix because R20.3 requires them, but they do not support library
  ranking claims.
- Local focused runs may prove harness and JSON generation. Retained CI
  bundles require at least two repeated runs per selected profile; additional
  local or CI repeats are required before interpreting overlapping confidence
  intervals.
- Optional variants, handwritten generated-style Mortar rows, join/page rows,
  update-batch rows, and controlled fake-JDBC rows are not R20.3 baseline rows.
  They move to R20.4 or R20.5 when scoped.
- The manual benchmark workflow must retain raw JSON, manifest, commands,
  summary, reviewer notes, and environment files without committing generated
  benchmark output.

The R20.4 generated fixed-read debate concluded:

- R20.4 isolates generated `findById` overhead instead of reinterpreting the
  full R20.3 matrix.
- The canonical R20.4 group is limited to ordinary JDBC `findById`, reusable
  prepared JDBC `findById`, tuned PgJDBC reusable JDBC `findById`, Mortar
  processor-generated `findById`, Mortar prepared processor-generated
  `findById`, and Mortar tuned processor-generated `findById`.
- Throughput, allocation, and sample-time latency are the only R20.4 profiles.
- Optional variants, handwritten generated-style Mortar rows, join/page rows,
  update-batch rows, jOOQ, QueryDSL SQL, and controlled fake-JDBC rows remain
  outside the R20.4 interpretation boundary.
- "Near-zero overhead" is an internal engineering target: the matched Mortar
  generated fixed-read path should not show a durable material regression
  versus the matched JDBC baseline on repeated retained runs with the same SQL
  shape, row shape, driver settings, and dataset. It is not public claim text.
- No optimization candidate can be proposed from R20.4 until retained raw JSON,
  manifest, commands, environment metadata, dataset notes, reviewer notes, and
  at least two repeated runs per selected profile exist.
- R20.4 `Done` means the generated fixed-read profiling harness, grouping
  guard, local instructions, and local smoke proof exist. It does not mean the
  retained evidence gate for optimization proposals or public performance
  reporting is satisfied.

The R20.5 DSL render/execute debate concluded:

- R20.5 should stay a narrow live PostgreSQL/Testcontainers measurement slice
  over broader existing DSL shapes, not a runtime optimization or library
  ranking slice.
- The canonical R20.5 group is limited to ordinary JDBC simple read, Mortar DSL
  render-per-call simple read, Mortar pre-rendered simple read, reusable
  prepared JDBC join/page, Mortar DSL join/page, reusable prepared JDBC update
  batch, and Mortar DSL update batch.
- The simple-read pre-rendered Mortar row is an internal isolate for render
  cost, not an external baseline. `plainJdbcFetch` remains the matched external
  ordinary JDBC baseline for that simple-read shape.
- The join/page and update-batch JDBC baselines are reusable prepared JDBC rows
  with the same SQL shape, bind values, result/update count shape, and row
  materialization or batch lifecycle as the Mortar DSL rows.
- `plainJdbcReusableStatementFetch` remains contextual R20.3 evidence, not a
  canonical R20.5 row, because R20.5 should not broaden the simple-read
  interpretation beyond the existing render-per-call versus pre-rendered
  isolate.
- Generated fixed-read R20.4 rows, optional variants, handwritten
  generated-style Mortar rows, jOOQ, QueryDSL SQL, rendering-only
  microbenchmarks, and controlled fake-JDBC rows remain outside R20.5.
- R20.5 local smoke JSON proves harness and preset wiring only. Optimization
  ranking remains blocked until retained raw JSON, manifest, commands,
  environment metadata, dataset notes, limitations, reviewer notes, and at
  least two repeated runs per selected profile exist.
- R20.5 `Done` means the DSL profiling harness, grouping guard, local
  instructions, and local smoke proof exist. It does not mean the retained
  evidence gate for optimization proposals or public performance reporting is
  satisfied.

The R20.6 Rust LSP resolver debate concluded:

- R20.6 measures the current Rust tooling/editor path only: source-map-backed
  generated-call resolution for hover, code actions, definition, diagnostics,
  metadata/source-map/snapshot reads, large-document scans, and current
  full-buffer change behavior.
- Criterion should be added now as an internal Rust harness, pinned to
  Criterion `0.7.0` because Mortar declares Rust `1.85` and Criterion `0.8.2`
  requires Rust `1.86`.
- The canonical scenario set covers canonical generated reads, supported local
  metamodel aliases, supported read-namespace aliases, unsupported alias
  fail-closed diagnostics, stale source-map fail-closed behavior, missing
  snapshot fail-closed behavior, malformed-buffer diagnostics, deterministic
  large-document scanning, and success-to-failure-to-recovery full-buffer edit
  scripts.
- Parser latency is measured separately from editor-feature resolution. Hover,
  code actions, definition, and diagnostics remain the current public LSP
  behavior surface; no benchmark-only resolver API or R19 semantic broadening
  is introduced.
- Incremental behavior is measured as current full-buffer replacement because
  the LSP advertises full text sync. R20.6 does not benchmark tree reuse,
  parser caching, or partial-sync behavior as if it already existed.
- R20.7 optimization candidates remain blocked until repeated retained
  artifacts, scenario metadata, raw Criterion output, profiler/allocation
  evidence, and benchmark-readiness review identify a dominant tooling cost.
- Rust tooling metrics stay in a separate interpretation boundary from Java
  JMH/PostgreSQL runtime metrics and do not support JDBC, database, or runtime
  performance claims.

## Current Benchmark Inventory

| Artifact | Purpose | Readiness |
| --- | --- | --- |
| `java/benchmarks/build.gradle.kts` | JMH dependencies, throughput, allocation, latency, PostgreSQL benchmark tasks, R20.4 generated fixed-read presets, and R20.5 DSL shape presets | Trustworthy as harness source; public reports still need retained raw artifacts |
| `PostgresRenderingBenchmark` | PostgreSQL SQL rendering microbenchmark | Internal-only; cannot imply database throughput |
| `ReferenceRenderingBenchmark` | jOOQ and QueryDSL rendering reference scenarios | Internal-only; comparable only for documented rendering shape |
| `JdbcExecutionBenchmark` | Controlled JDBC-double adapter overhead | Internal-only; fake JDBC doubles must never support real database claims |
| `PostgresExecutionBenchmark` | Live PostgreSQL/Testcontainers execution matrix | Best current runtime evidence source; still internal until retained clean-commit artifacts exist |
| `PostgresExecutionBenchmarkTest` | Sanity checks for benchmark scenario names, deterministic dataset, tuned PgJDBC parameters, and R20.4/R20.5 matrix guards | Trustworthy as harness guard, not performance evidence |
| `docs/benchmarks/baseline-2026-06-01.md` | Local rendering and controlled JDBC baseline | Internal-only; not public-ready |
| `docs/benchmarks/postgres-execution-2026-06-01.md` | Local real-PostgreSQL throughput, allocation, latency notes | Internal-only; not public-ready |
| `docs/benchmarks/performance-report-2026-06-01.md` | Public-readiness draft | Blocked for public claims until retained artifacts and commit metadata exist |
| `docs/benchmarks/thresholds.json` | Bootstrap threshold shape check | Not a real regression threshold |
| `rust/crates/mortar-lsp/src/lib.rs` | Current LSP parser/resolver implementation and tests | Correctness evidence; no benchmark-only public API |
| `rust/crates/mortar-lsp/benches/r20_lsp_resolver.rs` | Criterion harness for R20.6 Rust LSP resolver/editor-feature scenarios | Internal-only tooling benchmark source; public claims blocked |
| `rust/crates/mortar-lsp/tests/r20_lsp_benchmark_harness.rs` | Fixture coverage for R20.6 benchmark scenarios | Trustworthy as harness guard, not performance evidence |
| `docs/lsp.md` | LSP behavior and Java/Rust boundary docs | Trustworthy for semantic boundary; not performance evidence |

## Required Environment Metadata

Every retained runtime benchmark artifact bundle must record:

- commit SHA, branch or tag, date, and clean-worktree confirmation;
- exact command, include regex, JMH mode, warmup iterations, measurement
  iterations, forks, durations, time unit, result format, and result file path;
- OS, kernel or Windows build, CPU model, core count, memory, power/performance
  policy when known, and whether the run was local or CI;
- JDK vendor and version, JVM flags, Gradle version, and JMH version;
- PostgreSQL image/version, Docker version, Testcontainers version, PgJDBC
  version, JDBC URL parameters, `prepareThreshold`, statement-cache settings,
  binary transfer setting, and `plan_cache_mode` if changed;
- dataset schema, seed size, target query IDs, indexes, EXPLAIN output for
  plan-sensitive claims, and row materialization shape;
- raw JMH JSON for every repeated run and a manifest tying result files to
  benchmark source files.

Every retained Rust tooling benchmark artifact bundle must record:

- commit SHA, branch or tag, date, and clean-worktree confirmation;
- exact Cargo command, crate, benchmark name, corpus fixture, and feature flags;
- Rust toolchain, Criterion version if used, OS, CPU, memory, and profiler flags;
- whether the scenario is full parse, incremental parse, resolver lookup,
  diagnostics, snapshot/source-map read, or command handling;
- raw Criterion output or other retained machine-readable result files.

## Repeatability Rules

- Run each public-candidate benchmark profile on a clean commit.
- Keep throughput, allocation, and latency as separate profiles.
- Retain at least two repeated raw JSON runs before interpreting direction, and
  more when confidence intervals overlap.
- Do not compare numbers across machines unless the report explicitly says the
  comparison is cross-environment and exploratory.
- Do not hand-copy terminal output into a public claim without a retained raw
  artifact and derived summary.
- Do not tighten `thresholds.json` until the threshold is derived from retained
  clean-commit artifacts and reviewed as a regression gate.

## Comparison Rules

Plain JDBC baselines must be named precisely:

- ordinary JDBC: prepare, bind, execute, map, and close per operation;
- tuned PgJDBC: ordinary lifecycle plus explicit PgJDBC settings;
- reusable prepared JDBC: caller-owned connection and reusable
  `PreparedStatement`;
- maximum handwritten JDBC: direct getters/setters, projection-index mapping,
  and no abstraction checks. This is the ceiling unless evidence proves
  otherwise on the same shape.

Mortar comparisons must use the same SQL shape, parameter values, result size,
row materialization target, and connection lifecycle as the named baseline.

jOOQ and QueryDSL comparisons must document statement type, bind behavior,
fetch mode, SQL shape, row materialization, type mapping, and whether generated
or dynamic library features are being measured. They are reference-library
comparisons, not universal library rankings.

## No-Fake-JDBC Policy

`JdbcExecutionBenchmark` uses controlled JDBC doubles. It may measure adapter
overhead inside a fake JDBC harness, but it must not be cited for:

- real PostgreSQL throughput;
- network or database latency;
- PgJDBC prepared-statement behavior;
- connection-pool behavior;
- public claims against JDBC, jOOQ, QueryDSL, or ORM workloads.

## Retained Artifacts Policy

Public-candidate benchmark evidence must be retained as downloadable raw
artifacts from the benchmark workflow or an explicitly documented local artifact
bundle. Build-directory JSON paths are acceptable as local engineering evidence
only while the files remain machine-local.

An artifact bundle must include:

- raw result files for each profile and repeated run;
- an environment manifest;
- exact commands;
- benchmark source commit;
- dataset and schema notes;
- derived summary produced from raw artifacts;
- reviewer sign-off notes.

## Public Claim Policy

R20.1/R20.2 block all public performance claims. A future public report can be
unblocked only when:

- retained raw artifacts exist for the exact claim;
- the report identifies the benchmark shape and named baseline;
- confidence intervals, repeated runs, allocation, and latency evidence support
  the wording;
- limitations are explicit;
- an independent benchmark-readiness review signs off;
- roadmap and plan docs record the evidence and the claim boundary.

Invalid or misleading claims include:

- "Mortar is faster than JDBC" without naming ordinary, tuned, reusable, or
  maximum handwritten JDBC;
- database throughput claims from rendering-only or fake-JDBC benchmarks;
- application-level claims from one indexed lookup over a deterministic fixture;
- claims based on local build output without retained artifacts;
- treating overlapping confidence intervals as a decisive win;
- mixing Java runtime metrics and Rust LSP/tooling metrics in one headline;
- claiming connection-pool or production tuning behavior without a dedicated
  benchmark.

## Canonical R20 Slice Plan

- R20.1: Benchmark readiness audit and source-backed research. Status: Done.
- R20.2: Canonical performance plan and public-claim policy. Status: Done.
- R20.3: Java runtime JMH/PostgreSQL baseline matrix with retained artifacts.
  Status: Done.
- R20.4: Generated fixed-read overhead and allocation profiling. Status: Done.
- R20.5: DSL query render/execute overhead profiling for broader read and write
  shapes. Status: Done.
- R20.6: Rust LSP resolver latency and allocation benchmark plan/harness.
  Status: Done.
- R20.7: Optimization candidates ranked only by retained evidence. Status:
  Planned.
- R20.8: Public performance report gate and reviewer sign-off. Status: Planned.
