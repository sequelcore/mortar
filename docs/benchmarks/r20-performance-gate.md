# R20 Performance Gate

Date: 2026-06-03
Status: Internal decision gate. No public performance claims.

## Decision

R20.7 and R20.8 are complete as decision gates:

- R20.7 closes with no optimization implementation authorized.
- R20.8 closes with a public-report no-go decision.
- R20 closes as a measurement and governance phase, not as a public
  performance-claim or optimization phase.

The xhigh performance/architecture debate concluded that R20.3-R20.6 prove
harness coverage, scenario coverage, and Java/Rust boundary-correct measurement
surfaces. They do not provide enough retained evidence to rank an optimization
candidate for implementation or to publish public performance claims.

## Evidence Ranking

### Java Runtime Candidates

| Candidate | Evidence source | Confidence | Risk | Affected module | Implementation authorized now | Required retained artifacts before implementation |
| --- | --- | --- | --- | --- | --- | --- |
| Generated binder/mapper tightening for fixed reads | R20.4 JMH preset and smoke coverage for generated fixed-read rows | Low for optimization ranking; harness is trustworthy, but evidence is local/internal | Medium: changes generated/runtime hot path and could alter JDBC binding or row mapping behavior | `java/processor`, `java/runtime-jdbc`, `java/benchmarks` | No | Repeated retained throughput, allocation, and latency JSON for R20.4 rows; manifest; exact commands; clean commit; environment metadata; dataset notes; limitations; reviewer notes identifying a dominant binder/mapper cost |
| Prepared generated-query lifecycle changes | R20.3/R20.4 matrix and local public-readiness draft | Low for implementation; local evidence suggests same-band behavior but does not isolate a durable lifecycle bottleneck | Medium: could change statement ownership, connection assumptions, or PgJDBC behavior | `java/runtime-jdbc`, generated executor contracts | No | Repeated retained R20.3/R20.4 artifacts, PgJDBC setting metadata, statement lifecycle notes, plan-sensitive evidence where relevant, and review proving the lifecycle is the dominant cost |
| Renderer reuse or pre-render caching for DSL reads | R20.5 DSL shape presets and smoke coverage | Low; current evidence separates render-per-call and pre-rendered rows but is not retained or reviewed | Medium: risks stale query state, parameter ordering mistakes, or API behavior drift | `java/core`, `java/dialect-postgres`, `java/runtime-jdbc` | No | Repeated retained R20.5 throughput/allocation/latency artifacts for simple read, join/page, and update-batch shapes; allocation evidence isolating renderer cost; limitations and reviewer notes |
| PgJDBC tuning defaults or driver-setting changes | R20.3/R20.4 tuned PgJDBC rows exist as benchmark variables | Low; benchmark-local settings are not product defaults | High: product defaults could affect application behavior and deployment expectations | Runtime configuration/docs only if later authorized | No | Dedicated retained artifacts showing the same workload under ordinary, reusable, and tuned PgJDBC settings; application-configuration boundary review; explicit non-default documentation |
| Benchmark threshold tightening | `docs/benchmarks/thresholds.json` bootstrap shape check | Low; thresholds are not derived from retained clean-commit evidence | Medium: can create noisy or misleading CI failures | `docs/benchmarks/thresholds.json`, benchmark workflow | No | Reviewed retained repeated baselines with stable variance, command/config hashes, and explicit regression-policy notes |

### Rust Tooling Candidates

| Candidate | Evidence source | Confidence | Risk | Affected module | Implementation authorized now | Required retained artifacts before implementation |
| --- | --- | --- | --- | --- | --- | --- |
| Parser construction reuse or parser caching | R20.6 Criterion parser group over current tree-sitter parsing scenarios | Low for implementation; harness is trustworthy, but output is local/unretained and lacks allocation/profiler evidence | Medium: could change diagnostics or fail-closed behavior if parser state is reused incorrectly | `rust/crates/mortar-lsp` | No | Repeated retained Criterion output, corpus metadata, exact Cargo command, Rust toolchain, OS/CPU metadata, profiler/allocation evidence, and review isolating parser construction as dominant cost |
| Source-map/snapshot lookup caching for hover/actions/definition | R20.6 editor-feature Criterion group and correctness tests | Low; scenario coverage exists, but no retained dominant-cost evidence | Medium: stale metadata and snapshot freshness are R19 trust boundaries | `rust/crates/mortar-lsp` | No | Repeated retained editor-feature Criterion output, snapshot/source-map fixture manifest, stale/missing artifact correctness review, allocation evidence, and reviewer notes |
| Diagnostics full-buffer scan optimization | R20.6 diagnostics/full-sync Criterion group and large-document tests | Low; current full-sync behavior is measured but not retained or profiled | Medium: diagnostics must keep reason-specific fail-closed behavior | `rust/crates/mortar-lsp` | No | Retained large-document and edit-script Criterion artifacts, profiler/allocation evidence, exact corpus size notes, and review proving scan cost dominates |
| Incremental parse or partial-sync strategy | R20.6 explicitly measures current full-buffer replacement only | Very low; current evidence does not measure partial sync or tree reuse | High: changes LSP synchronization semantics and R19 editor behavior assumptions | `rust/crates/mortar-lsp`, editor clients if protocol behavior changes | No | New scoped design/ADR if sync semantics change, retained incremental benchmark artifacts with old-tree reuse, correctness tests for edits and diagnostics, and editor behavior review |

## Blocked Candidates

All runtime and tooling optimizations are blocked for implementation from R20
evidence because the required retained evidence chain is incomplete:

- Java R20.3-R20.5 evidence is harness and local/internal evidence unless raw
  JSON, manifest, commands, environment metadata, dataset notes, limitations,
  and review notes are retained for repeated clean-commit runs.
- Rust R20.6 evidence is harness and local/internal tooling evidence unless raw
  Criterion output, scenario metadata, exact commands, toolchain/environment
  metadata, profiler/allocation evidence, limitations, and review notes are
  retained.
- The public-readiness draft still references local build-output JSON and a
  pending release commit, so it cannot support public claims.
- Controlled fake-JDBC benchmark rows remain excluded from database,
  PostgreSQL, driver, or product performance claims.

## Benchmark-Readiness Review

Readiness result: blocked for public performance reporting.

The R20 evidence does record benchmark identities, commands, harness locations,
and scenario boundaries. Public readiness is blocked because the required
evidence is not fully reproducible or externally reviewable:

- retained CI or explicit retained local artifact bundles are not attached to
  this source-controlled gate;
- release or clean benchmark commit metadata is absent from the public-readiness
  draft;
- local build paths are cited as raw result locations;
- independent reviewer sign-off against retained artifacts is absent;
- no broader workload evidence exists for application-level claims;
- Java runtime and Rust tooling metrics are separate evidence categories and
  cannot be merged into a single product-performance headline.

## Public Report Decision

Public report: no-go.

Mortar may keep the existing `performance-report-2026-06-01.md` as an internal
public-readiness draft, but it must not be published or used as public claim
text. Public wording is limited to: Mortar has internal/local benchmark harnesses
and baseline drafts, and public performance claims remain blocked until retained
artifacts and benchmark-readiness review exist for the exact claim.

Invalid public wording includes:

- broad "Mortar is faster than JDBC" claims;
- database claims from rendering-only or controlled fake-JDBC benchmarks;
- Java runtime claims from Rust LSP/tooling measurements;
- application-level claims from one deterministic indexed lookup;
- claims based only on local build-output JSON or terminal output.

## R20 Closure

R20 closes professionally by recording what is proven and what is not:

- proven: benchmark harnesses, scenario guards, local smoke evidence, internal
  baseline drafts, and public-claim rules exist;
- not proven: a retained evidence-backed optimization target, a public
  performance claim, or a product/runtime behavior change;
- deferred: retained repeated benchmark artifact collection, benchmark-readiness
  sign-off against those artifacts, and any future optimization slice justified
  by that retained evidence.

No release, tag, publication, migration, public report, public performance
claim, runtime optimization, Java API change, generated Java API change, R19
editor semantic change, or Rust tooling behavior change is authorized by this
gate.
