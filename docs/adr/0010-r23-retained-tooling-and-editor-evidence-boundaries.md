# ADR-0010: R23 Retained Tooling And Editor Evidence Boundaries

Date: 2026-06-03
Status: Accepted

## Context

R23 completes Mortar's retained performance evidence gate after R22 added scalar
reads and mutation contracts. The evidence program now has three separate
families:

- Java runtime evidence from JMH over live PostgreSQL/Testcontainers;
- Rust tooling evidence from Criterion over parser, resolver, diagnostics,
  source-map, and snapshot paths;
- VS Code editor-latency evidence from client-visible extension-host smoke
  traces.

These families answer different questions and cannot be merged into a single
product-performance claim.

## Decision

R23.3 retains Rust tooling evidence under `r23.3-rust-tooling-lsp` with schema
`mortar-r23-rust-tooling-criterion-manifest-v1`. It may use the existing
R20/R19 LSP corpus, but retained artifacts use R23 bundle names and R23
Criterion group names.

R23.4 retains VS Code editor-latency evidence under
`r23.4-vscode-editor-latency` with schema
`mortar-r23-vscode-editor-latency-manifest-v1`. Its trace boundary is
client-visible VS Code extension-host behavior: hover, code actions, definition,
diagnostics, copy SQL, and EXPLAIN command invocation where PostgreSQL is
available.

R23.5 authorizes no optimization by default. Optimization can proceed only if
retained repeated clean-commit bundles identify a material dominant cost above
noise and profiler or allocation evidence isolates that cost.

## Consequences

- Rust Criterion timing is not VS Code editor UX latency.
- VS Code smoke or screenshot traces are not Java runtime or database
  performance evidence.
- EXPLAIN command timing is a separate adapter-path scenario because it crosses
  the VS Code client, Mortar CLI, and PostgreSQL.
- No production runtime, public API, editor semantic behavior, or benchmark
  threshold changes are authorized by R23.3 or R23.4 evidence collection.
- R23 may close R23.6 and R23.7 as an explicit no-go if R23.5 does not approve
  an evidence-ranked optimization.

## No-Go Items

- No benchmark-only APIs, caches, flags, or shortcuts.
- No Java runtime claim from Rust Criterion or VS Code trace artifacts.
- No editor latency claim from server-side Criterion measurements.
- No public performance wording from a single retained run or unreviewed
  artifact.
- No parser caching, source-map caching, diagnostics optimization, partial-sync
  strategy, or runtime optimization without a separate evidence-ranked
  authorization.
