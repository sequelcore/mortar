# R23 Benchmark Readiness Review

Date: 2026-06-03
Status: In Progress. Remote retained artifact production and review remain
pending. Public performance claims remain blocked.

## Scope

This review covers R23 retained evidence for:

- R23.2 Java runtime PostgreSQL/JMH evidence;
- R23.3 Rust tooling/LSP Criterion evidence;
- R23.4 VS Code editor-latency trace evidence.

The evidence families are intentionally separate. A bundle from one family
cannot support claims about another family.

## Required Bundle Checks

Every retained R23 bundle must include:

- raw result output for every repeated run;
- exact commands and selected inputs;
- commit SHA, branch, date, and clean-worktree state before measurement;
- toolchain and runner metadata;
- dataset or corpus notes;
- derived summary;
- limitations and unsupported rows or scenarios;
- reviewer notes and explicit sign-off state.

Java runtime bundles must also record PostgreSQL/Testcontainers/PgJDBC/Hikari
applicability metadata. Rust tooling bundles must record Criterion output and
Rust toolchain metadata. VS Code bundles must retain client-visible trace JSON
and extension-host test output.

## Current Readiness

R23.2 has a retained workflow definition for post-R22 scalar, mutation,
returning, and batch rows. It is not public-ready until a clean-commit remote
artifact bundle exists and reviewer sign-off is completed against that bundle.

R23.3 has a retained workflow definition for `r23.3-rust-tooling-lsp`. It is
not optimization-ready because profiler/allocation evidence is not part of the
default retained bundle.

R23.4 has a retained workflow definition for
`r23.4-vscode-editor-latency`. It is client-visible editor evidence, not Rust
Criterion timing. EXPLAIN command timing is interpreted separately from hover,
code action, definition, diagnostics, and copy SQL.

## Current Readiness Decision

Benchmark readiness for public performance claims is currently no-go because
remote retained artifacts and reviewer sign-off are not complete.

Allowed internal wording:

Mortar has retained-evidence workflows for Java runtime, Rust tooling, and VS
Code editor-latency review. Public performance claims remain blocked until the
exact claim has retained artifacts, reproducibility notes, limitations, and
benchmark-readiness sign-off.

Disallowed wording:

- Mortar is faster than JDBC.
- Mortar is faster than PostgreSQL access through direct JDBC.
- Rust tooling benchmarks prove editor UX latency.
- VS Code smoke traces prove Java runtime performance.

## Required Follow-Up Before Any Claim

- run the manual retained workflow on the exact clean commit;
- review downloaded artifacts before interpreting results;
- attach or cite artifact names and workflow run identifiers in the decision
  record;
- add profiler or allocation evidence before authorizing optimization;
- keep public wording exact, narrow, and tied to the retained artifacts.
