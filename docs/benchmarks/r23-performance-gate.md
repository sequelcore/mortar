# R23 Performance Gate

Date: 2026-06-03
Status: Closed no-go for R23 optimization. No optimization is authorized.

## Decision

R23.5 closes as a no-optimization decision. Retained repeated artifacts were
produced and reviewed, but they do not meet the R23 optimization criteria. The
evidence did not include profiler/allocation isolation, did not provide at
least three clean retained bundles for any one candidate scenario family, and
did not isolate a material dominant cost above noise.

## Optimization Criteria

An R23 optimization can be authorized only when all conditions are true:

- at least three retained clean-commit bundles exist for the scenario family;
- the issue is material and reproducible across repeated artifacts;
- profiler or allocation evidence isolates the suspected dominant cost;
- the expected change preserves public API shape and Clean Architecture
  boundaries;
- the change applies to real product behavior, not only benchmark setup;
- expected gain is at least 10% in one primary metric with no meaningful
  paired-metric regression;
- run-to-run spread and confidence intervals do not put results in the same
  band;
- benchmark-readiness review approves the evidence boundary.

## Candidate Ranking

| Candidate | Evidence family | Current evidence | Authorized |
| --- | --- | --- | --- |
| Java runtime scalar/mutation optimization | R23.2 Java runtime | Two targeted repeated throughput bundles retained for scalar count and DML `RETURNING`; no allocation/profiler isolation and no full-matrix public-ready evidence | No |
| Rust parser or resolver caching | R23.3 Rust tooling | Repeated Criterion bundles retained; no profiler/allocation isolation or dominant-cost proof | No |
| Diagnostics full-sync optimization | R23.3 Rust tooling | Repeated Criterion bundles retained; no dominant-cost proof above noise | No |
| VS Code editor-latency optimization | R23.4 editor latency | Repeated smoke traces retained; no isolated extension-host dominant cost | No |
| Public performance wording | R23.2-R23.4 | Retained internal evidence exists, but public-claim readiness is no-go | No |

## R23.6 And R23.7 Expected Closure

R23.6 closes as not authorized. No performance optimization is implemented.
R23 did include two correctness/portability fixes required by retained editor
evidence on Linux: platform executable resolution in the VS Code extension and
valid Unix file URI formatting in the LSP. These are not performance
optimizations and do not authorize public performance wording.

R23.7 before/after retained review is not applicable because R23.6 did not run.
Any future before/after evidence must be created by a new, scoped optimization
slice after the optimization criteria are met.

## Public Wording Decision

Public performance claims remain no-go after retained artifact review.

Allowed wording is limited to measurement discipline:

Mortar maintains retained benchmark workflows for internal Java runtime, Rust
tooling, and VS Code editor-latency evidence. Public performance claims remain
blocked until retained artifacts and benchmark-readiness review support the
exact wording.

No release, tag, publication, threshold tightening, optimization, public report,
or broad performance claim is authorized by this gate.
