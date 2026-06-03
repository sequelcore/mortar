# R23 Retained Performance Evidence Plan

Date: 2026-06-03
Status: Planned. Design and evidence policy only. No optimization or public
performance claim is authorized.

## Purpose

R23 finishes Mortar's performance program by turning the R20 harnesses into a
retained, reviewable evidence pipeline for the post-R22 API surface. R20 proved
measurement discipline and closed with no optimization or public-report
authorization. R22 added scalar reads and mutation contracts, so R23 must plan
the evidence needed before R24 pre-release readiness can evaluate performance
risk.

R23 keeps three evidence boundaries separate:

- Java runtime evidence: JMH over live PostgreSQL/Testcontainers and JDBC.
- Rust tooling evidence: Criterion and profiler evidence for compiler/LSP
  tooling.
- Editor latency evidence: client-visible hover, code action, definition, and
  diagnostics timing. This is not Java runtime or database evidence.

## What R20 Left Unresolved

R20 left these items deliberately unresolved:

- no retained post-R22 benchmark matrix exists for `count`, `exists`,
  row-count mutations, `RETURNING` mutations, or same-SQL non-returning batch
  writes;
- the manual benchmark workflow retains an R20.3 Java baseline bundle, but its
  bundle names and manifest schema are not yet generalized for R23 evidence
  families;
- R20.4 generated fixed-read and R20.5 DSL shape presets are local harnesses
  until repeated raw artifacts, manifests, environment metadata, limitations,
  and review notes are retained;
- R20.6 Criterion output is tooling evidence only and lacks a retained R23
  bundle with profiler or allocation evidence;
- no optimization candidate has retained evidence proving a dominant cost;
- no public performance claim has exact retained artifacts and reviewer
  sign-off.

## Post-R22 Scenario Matrix

R23 Java runtime planning must cover the executable post-R22 surface without
inventing benchmark-only APIs.

| Family | Required Mortar rows | Matched baselines | R23 status |
| --- | --- | --- | --- |
| Generated fixed read | processor-generated `findById`, prepared generated `findById`, tuned generated `findById` | ordinary JDBC `findById`, reusable prepared JDBC `findById`, tuned reusable PgJDBC `findById` | Existing R20.4 harness; needs retained R23 bundle |
| DSL read | render-per-call simple read, pre-rendered simple read | ordinary JDBC simple read; reusable JDBC only when lifecycle matches | Existing R20.5 harness; needs retained R23 bundle |
| Join/page read | DSL joined ordered page | reusable prepared JDBC with same SQL, bind order, page size, offset, and mapper | Existing R20.5 harness; needs retained R23 bundle |
| Scalar read | DSL `count`, DSL `exists` | plain JDBC scalar rows with same SQL, bind order, scalar type, and statement lifecycle | Gap after R22; plan only in R23.2 |
| Row-count mutation | insert/update/delete row-count mutations | plain JDBC update rows with same SQL, bind order, and update-count interpretation | Gap after R22; plan only in R23.2 |
| Returning mutation | PostgreSQL `RETURNING` insert/update/delete where supported by public API | plain JDBC `RETURNING` query rows with same returning columns and mapper | Gap after R22; plan only in R23.2 |
| Batch write | same-SQL non-returning batch writes | reusable prepared JDBC batch with same SQL, bind order, batch size, and update counts | Existing update-batch row plus R22 batch contract review |

Generated scalar facades, generated write namespaces, generated repositories,
and self-executing query objects are excluded because R22 explicitly rejected
them.

## Fair Comparisons

Fair Java comparisons require the same SQL shape, bind values, bind order,
statement lifecycle, connection lifecycle, result size, row or scalar
materialization, transaction assumptions, dataset, and PgJDBC settings.

Allowed baseline names:

- ordinary JDBC: prepare, bind, execute, map, and close per operation;
- reusable prepared JDBC: caller-owned connection and reusable
  `PreparedStatement`;
- tuned PgJDBC: benchmark-local PgJDBC settings such as `prepareThreshold`,
  statement-cache size, and binary transfer, never Mortar defaults;
- jOOQ reference row or QueryDSL SQL reference row only when statement type,
  bind behavior, fetch behavior, SQL shape, row materialization, and type
  mapping are documented.

Excluded comparisons:

- broad "Mortar is faster than JDBC" comparisons;
- Hibernate/JPA or ORM workload comparisons in R23;
- database or runtime claims from controlled JDBC-double benchmarks;
- Java runtime claims from rendering-only JMH benchmarks;
- Java runtime claims from Rust Criterion or editor latency evidence;
- jOOQ or QueryDSL ranking claims unless a dedicated retained scenario proves
  the exact wording.

## Retained Artifact Bundle

An R23 optimization-decision bundle is the minimum evidence unit for internal
engineering decisions. Each bundle must include:

- raw JMH JSON or raw Criterion output for every repeated run;
- throughput, allocation, and latency profiles where the scenario supports
  them;
- exact commands, include regexes, profile settings, fork/warmup/measurement
  counts, result paths, and feature flags;
- commit SHA, branch or tag, date, and clean-worktree confirmation;
- OS, CPU, memory, JDK or Rust toolchain, Gradle or Cargo version, and profiler
  settings;
- PostgreSQL image/version, Docker version, Testcontainers version, PgJDBC
  version, JDBC URL parameters, `prepareThreshold`, statement-cache settings,
  binary transfer setting, and `plan_cache_mode` if changed;
- dataset or corpus manifest, schema notes, row counts, indexes, target query
  IDs, and EXPLAIN output for plan-sensitive Java claims;
- derived summary generated from raw artifacts;
- limitations and failed or unsupported rows;
- reviewer notes;
- dominant-cost evidence: JFR or async-profiler for Java, profiler/allocation
  evidence for Rust tooling, or request-trace timing for editor latency.

Build-directory JSON, terminal output, local smoke output, and unbundled
Criterion directories are harness proof only. They are not optimization
authorization and are never public evidence.

## Optimization Authorization Criteria

R23 optimization is split from evidence collection. An optimization candidate
can move forward only when all criteria are met:

- at least three retained clean-commit runs exist for the scenario family;
- the candidate shows a consistent material issue in retained throughput,
  allocation, or latency evidence;
- the suspected cost is isolated by profiler or allocation artifacts;
- the expected change preserves public API shape and architecture boundaries;
- the change is not benchmark-specific and applies to real repository code;
- the expected gain is at least 10% in one primary metric with no meaningful
  regression in paired metrics;
- results are not same-band because of overlapping error bars or run-to-run
  spread;
- a benchmark-readiness review approves the evidence boundary.

`repeatCount >= 2` is enough to retain evidence but not enough by itself to
authorize optimization. `docs/benchmarks/thresholds.json` must not be tightened
until at least five retained clean-commit bundles show stable variance on the
same runner class and the threshold has a reviewed regression policy.

If no scenario meets these criteria, R23 should record a no-optimization
decision instead of forcing a change.

## Public-Claim Criteria

Public performance wording remains no-go unless all criteria below are met for
the exact claim:

- retained raw artifacts exist for the exact scenario and baseline;
- the benchmark source, command, environment, dataset, limitations, and commit
  are disclosed;
- the claim names the exact baseline, such as ordinary JDBC, reusable prepared
  JDBC, tuned PgJDBC, jOOQ reference row, or QueryDSL SQL reference row;
- throughput, allocation, and latency evidence support the wording;
- confidence intervals and run-to-run spread do not contradict the wording;
- reviewer sign-off approves benchmark readiness and public-claim discipline;
- docs separate Java runtime, Rust tooling, and editor latency evidence.

R23 should not pursue public Rust tooling or editor-latency claims. Those
families are internal engineering evidence unless a later roadmap slice defines
a public tooling-report standard.

## R23 Slices

- R23.1 Benchmark planning, research, and xhigh debate. Status: Planned.
- R23.2 Post-R22 Java runtime benchmark matrix and retained artifact workflow.
  Status: Planned.
- R23.3 Rust tooling benchmark retained evidence. Status: Planned.
- R23.4 Editor-latency evidence boundary and retained trace format. Status:
  Planned.
- R23.5 Benchmark-readiness review and evidence-ranked optimization decision.
  Status: Planned.
- R23.6 Evidence-backed optimization implementation only if authorized. Status:
  Planned.
- R23.7 Before/after retained benchmark review. Status: Planned.
- R23.8 Public performance wording go/no-go. Status: Planned.

R23.6 and R23.7 may close as "not authorized" if retained evidence does not
identify a dominant cost. R24 depends on that explicit go/no-go.

## Workflow Retention Plan

Normal CI should keep compile, test, and benchmark-contract checks. Retained
benchmark evidence should remain manual and run only on clean commits.

R23 workflow planning should generalize the R20.3 pattern without implementing
it in this design slice:

- Java runtime bundles should use an R23 bundle id and artifact path, not
  `r20.3`;
- Rust tooling bundles should retain Criterion output, commands, toolchain
  metadata, corpus manifests, profiler/allocation artifacts, limitations, and
  review notes separately from Java runtime bundles;
- editor-latency bundles should retain client-visible timing traces and editor
  environment metadata separately from Rust parser/resolver Criterion output;
- artifact retention should preserve the existing manual workflow discipline
  and documented retention period;
- local smoke output remains uncommitted build output.

## Xhigh Debate Outcome

The xhigh architecture debate concluded that R23 must stay evidence-first and
split optimization into gated sub-slices. Required scenarios are generated
fixed reads, DSL reads, join/page reads, R22 scalar reads, row-count mutations,
returning mutations, and same-SQL non-returning batch writes. Comparisons are
fair only when SQL, bindings, lifecycle, materialization, dataset, and driver
settings match. Java runtime evidence, Rust tooling evidence, and editor
latency evidence are separate artifact families. Optimization starts only after
retained evidence isolates a dominant cost above noise. Public claims remain
forbidden unless the exact claim has retained artifacts and benchmark-readiness
sign-off.

## Research Basis

- OpenJDK JMH documents JMH as a JVM benchmark harness and warns that benchmark
  setup and peer review are part of credible results:
  https://github.com/openjdk/jmh
- JMH samples document dead-code, blackhole, loop, fork, setup, and profiler
  pitfalls:
  https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples
- PgJDBC documents server-prepared statement behavior, `prepareThreshold`,
  prepared-statement cache settings, and binary transfer:
  https://jdbc.postgresql.org/documentation/server-prepare/ and
  https://jdbc.postgresql.org/documentation/use/
- PostgreSQL documents extended query protocol flow, prepared statement plan
  behavior, and EXPLAIN:
  https://www.postgresql.org/docs/current/protocol-flow.html,
  https://www.postgresql.org/docs/current/sql-prepare.html, and
  https://www.postgresql.org/docs/current/sql-explain.html
- Testcontainers documents PostgreSQL containers for repeatable integration
  runs:
  https://java.testcontainers.org/modules/databases/postgres/
- jOOQ documents statement type, statement reuse, and performance
  considerations:
  https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/custom-settings/settings-statement-type/,
  https://www.jooq.org/doc/latest/manual/sql-execution/reusing-statements/,
  and https://www.jooq.org/doc/latest/manual/sql-execution/performance-considerations/
- QueryDSL SQL exposes SQL/JDBC query APIs, fetch operations, and rendered SQL
  extraction:
  https://querydsl.com/static/querydsl/5.0.0/apidocs/com/querydsl/sql/SQLQuery.html
- async-profiler and JFR provide profiler evidence for Java CPU, heap, and
  allocation analysis:
  https://github.com/async-profiler/async-profiler and
  https://docs.oracle.com/en/java/javase/21/docs/specs/man/jfr.html
- Criterion documents warmup, statistical analysis, and profiling mode for
  Rust benchmarks:
  https://bheisler.github.io/criterion.rs/book/index.html,
  https://bheisler.github.io/criterion.rs/book/analysis.html, and
  https://bheisler.github.io/criterion.rs/book/user_guide/profiling.html
- ACM artifact review and SPEC run rules support retained artifacts,
  repeatability, reproducibility, configuration disclosure, and public-claim
  discipline:
  https://www.acm.org/publications/policies/artifact-review-and-badging-current
  and https://www.spec.org/cpu2026/docs/runrules.html
- GitHub Actions artifact documentation supports workflow artifact retention
  through `upload-artifact`:
  https://docs.github.com/actions/using-workflows/storing-workflow-data-as-artifacts

