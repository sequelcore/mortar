# R23 Performance Gate

Date: 2026-06-03
Status: Draft optimization gate pending remote retained artifact review. No
optimization is currently authorized.

## Decision

R23.5 must close as a no-optimization decision unless retained repeated
artifacts prove a dominant cost. At this point, before remote retained artifact
review is complete, R23.6 optimization is not authorized.

This is a blocked-before-evidence decision, not a deferred implementation
claim. The final R23.5 decision must be updated after retained artifacts are
produced and reviewed.

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
| Java runtime scalar/mutation optimization | R23.2 Java runtime | Workflow and harness exist; remote retained artifacts and profiler evidence still need review | No |
| Rust parser or resolver caching | R23.3 Rust tooling | Criterion retained workflow exists; no profiler/allocation isolation | No |
| Diagnostics full-sync optimization | R23.3 Rust tooling | Criterion retained workflow exists; no dominant-cost proof | No |
| VS Code editor-latency optimization | R23.4 editor latency | Smoke trace workflow exists; no isolated extension-host dominant cost | No |
| Public performance wording | R23.2-R23.4 | Evidence workflows exist, but public-claim readiness is no-go | No |

## R23.6 And R23.7 Expected Closure

R23.6 is not authorized before retained artifact review. No product-code
optimization is implemented.

R23.7 before/after retained review is expected to be not applicable unless
R23.5 authorizes an optimization. Any future before/after evidence must be
created by a new, scoped optimization slice after R23.5 criteria are met.

## Public Wording Decision

Public performance claims remain no-go before retained artifact review.

Allowed wording is limited to measurement discipline:

Mortar maintains retained benchmark workflows for internal Java runtime, Rust
tooling, and VS Code editor-latency evidence. Public performance claims remain
blocked until retained artifacts and benchmark-readiness review support the
exact wording.

No release, tag, publication, threshold tightening, optimization, public report,
or broad performance claim is authorized by this gate.
