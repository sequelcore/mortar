# R23 Benchmark Readiness Review

Date: 2026-06-03
Status: Complete for R23. Remote retained artifacts were produced and reviewed
for the R23 evidence gate. Public performance claims remain blocked.

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

## Reviewed Remote Artifacts

All reviewed bundles were produced from commit
`28303a2f499591fab5bf6e7db1336393fc9cc504` on branch
`r23-retained-evidence` with clean-worktree metadata retained in the bundle
manifest.

- R23.2 Java scalar count:
  https://github.com/sequelcore/mortar/actions/runs/26887593414,
  artifact `mortar-r23.2-post-r22-java-runtime-throughput-28303a2f499591fab5bf6e7db1336393fc9cc504`.
- R23.2 Java DML `RETURNING` fetch:
  https://github.com/sequelcore/mortar/actions/runs/26887593463,
  artifact `mortar-r23.2-post-r22-java-runtime-throughput-28303a2f499591fab5bf6e7db1336393fc9cc504`.
- R23.3 Rust tooling/LSP:
  https://github.com/sequelcore/mortar/actions/runs/26887800615,
  artifact `mortar-r23.3-rust-tooling-lsp-28303a2f499591fab5bf6e7db1336393fc9cc504`.
- R23.4 VS Code editor-latency:
  https://github.com/sequelcore/mortar/actions/runs/26885861833,
  artifact `mortar-r23.4-vscode-editor-latency-smoke-28303a2f499591fab5bf6e7db1336393fc9cc504`.

Cancelled or failed exploratory runs were not used as evidence:

- `26883576502` and `26885924635` were broad Java runs cancelled because they
  exceeded the intended evidence pass;
- `26883576468` and `26884141147` were editor runs that exposed Linux
  extension/LSP portability defects fixed before the accepted retained run.

## Readiness Review

R23.2 retained two targeted, repeated Java runtime throughput bundles: one
scalar `count` row and one DML `RETURNING` row. These bundles prove the retained
workflow and post-R22 runtime evidence path. They do not represent the full
R23.2 matrix and do not support public comparative wording.

R23.3 retained repeated Criterion output for parser, feature-resolution,
diagnostics, source-map, snapshot, and fail-closed LSP paths from the existing
R20/R19 corpus under R23 group names. It is tooling evidence, not editor UX
latency.

R23.4 retained repeated VS Code smoke trace output for hover, code action,
definition, diagnostics, copy SQL, and EXPLAIN-command boundaries. It is
client-visible editor evidence, not Rust Criterion timing and not Java runtime
or PostgreSQL throughput evidence.

## Current Readiness Decision

Benchmark readiness for retained internal evidence is accepted for R23. The
reviewed bundles are reproducible enough to close R23 as an evidence gate.

Optimization is not authorized. The reviewed evidence did not include the
required profiler or allocation isolation, did not include at least three
retained clean-commit bundles for a candidate scenario family, and did not
identify a material dominant cost above noise.

Public performance claims are no-go. The reviewed artifacts are internal
evidence only and do not support broad or comparative public wording.

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

## Required Follow-Up Before Any Future Claim

- run the manual retained workflow on the exact clean commit;
- review downloaded artifacts before interpreting results;
- attach or cite artifact names and workflow run identifiers in the decision
  record;
- add profiler or allocation evidence before authorizing optimization;
- keep public wording exact, narrow, and tied to the retained artifacts.
