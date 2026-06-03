# Mortar Roadmap

Date: 2026-06-03
Status: Canonical

Mortar is Java-first, refactor-safe, SQL-transparent query authoring for Spring
and PostgreSQL. This roadmap records current public status and near-term
readiness work. Git history remains the detailed project history.

## Current Status

Mortar is pre-release. No public artifact has been published, no public release
tag has been created, and the first alpha remains gated by R24 readiness work.

Supported current scope:

- Java 21, Spring Boot 3.5.x, JDBC, and PostgreSQL 16 evidence.
- Framework-free core query model and DSL.
- Java annotation processor for generated `Q*` metamodels.
- Generated fixed reads for `findById` and explicit `findAll`.
- DSL reads, projections, joins, sorting, pagination, scalar `count`, scalar
  `exists`, and explicit mutations including PostgreSQL `RETURNING`.
- PostgreSQL renderer, JDBC runtime, Spring Boot starter, testkit, SQL
  snapshots, metadata, CLI, LSP, and VS Code SQL visibility.

Current limits:

- PostgreSQL is the only supported dialect.
- Mortar is not a full ORM and does not provide lazy loading, identity maps,
  aggregate graph loading, or Spring Data-style repository derivation.
- Performance claims are limited to measurement discipline and retained
  evidence boundaries; Mortar does not claim broad superiority over JDBC,
  PostgreSQL, jOOQ, QueryDSL, Hibernate, or Spring Data.
- Publication readiness is not complete until packaging dry-runs, repository
  health, security posture, examples, API/Javadocs, and the final alpha
  go/no-go decision close.

## Completed Capability Milestones

| Milestone | Status | Public outcome |
| --- | --- | --- |
| R0-R6 Foundation through Spring Boot integration | Done | Multi-module Java/Rust project, public governance files, PostgreSQL rendering, JDBC runtime, and Spring starter. |
| R7-R15 Tooling, diagnostics, examples, release policy, and API hardening | Done | CLI, metadata, SQL snapshots, editor tooling, examples, release policy, and public API documentation. |
| R16-R19 Generated reads, refactor safety, metadata/source maps, and editor semantics | Done | Generated fixed reads, source-map-backed SQL visibility, bounded LSP/VS Code behavior, and fail-closed tooling contracts. |
| R20 Performance measurement discipline | Done | JMH/Criterion benchmark harnesses and public-claim policy established without authorizing optimization or public claims. |
| R21 Query recipes | Done | Public repository authoring recipes and DDD/Clean Architecture usage guidance. |
| R22 Scalar and mutation contracts | Done | DSL scalar `count`/`exists`, row-count mutations, `RETURNING`, and JDBC execution paths. |
| R23 Retained performance evidence and optimization decision | Done | Retained Java runtime, Rust tooling, and VS Code editor-latency evidence reviewed; optimization no-go and public performance claims remain blocked. |
| R24 Public documentation and pre-release readiness | In Progress | R24.2/R24.3 documentation audit, rewrite, and cleanup are complete; remaining readiness slices are planned. |

R23 retained evidence:

- Java scalar count artifact:
  https://github.com/sequelcore/mortar/actions/runs/26887593414
- Java DML `RETURNING` artifact:
  https://github.com/sequelcore/mortar/actions/runs/26887593463
- Rust tooling/LSP artifact:
  https://github.com/sequelcore/mortar/actions/runs/26887800615
- VS Code editor-latency artifact:
  https://github.com/sequelcore/mortar/actions/runs/26885861833

The R23 evidence proves retained benchmark workflow coverage for the reviewed
families. It does not authorize product optimization or public performance
superiority wording.

## R24: Public Documentation And Pre-Release Readiness

Status: In Progress

Purpose: prepare Mortar for a first public alpha readiness decision. R24 is a
readiness program, not an artifact publication step.

| Slice | Status | Outcome |
| --- | --- | --- |
| R24.1 Documentation canonization strategy and readiness plan | Done | Public documentation ownership and readiness criteria defined. |
| R24.2 Public documentation canon audit | Done | Public docs classified; obsolete internal research, baseline, report, and planning residue identified for deletion or synthesis. |
| R24.3 Public documentation rewrite and cleanup | Done | README, roadmap, plan, performance docs, benchmark docs, release policy, and topical guides rewritten for current public scope. |
| R24.4 API and Javadocs readiness review | Planned | Verify public Java APIs, generated-source Javadocs, diagnostics, and examples against current source. |
| R24.5 Examples and first-user path readiness | Planned | Prove README-to-example path and Clean Architecture example for a new user. |
| R24.6 Packaging and publishing dry-runs | Planned | Verify Maven Central, Gradle local publishing, Cargo dry-runs, VS Code packaging, and publication workflow behavior without publishing. |
| R24.7 CI, repository health, and security readiness | Planned | Review health files, CI permissions, dependency/code scanning posture, and security policy. |
| R24.8 Performance wording and benchmark evidence review | Planned | Re-check all public performance wording against retained evidence and claim boundaries. |
| R24.9 `0.1.0-alpha` go/no-go decision | Planned | Decide whether alpha publication can proceed after R24.4-R24.8 close. |

## R24.2/R24.3 Decision Record

The documentation cleanup keeps public docs current-state focused. Old roadmap
construction detail, internal benchmark baselines, research notes, report
templates, and planning residue were not moved into archive or history folders;
durable history remains in git, ADRs, release policy, changelog, retained
benchmark artifacts, and concise roadmap status.

Architecture decision:

- `README.md` is the public entry point.
- `docs/roadmap.md` is the short canonical roadmap and public status source.
- `docs/plan.md` is the current R24 readiness plan and audit outcome.
- Topical guides own usage, examples, troubleshooting, metadata, editor, and
  performance documentation.
- `docs/adr/` keeps real architecture decisions, even when decision titles use
  historical slice identifiers.
- `docs/release.md` owns versioning, artifact, compatibility, and
  publication policy.
- Benchmark docs describe current commands, retained evidence boundaries, and
  non-claims; old local-only reports and planning gates are not public canon.

Research basis for the R24 documentation posture:

- Sonatype Central Portal publishing and requirements.
- Gradle Maven Publish documentation.
- GitHub community health, repository security, and GitHub Actions secure-use
  documentation.
- OpenSSF Scorecard guidance.
- Cargo publishing and package inspection documentation.
- VS Code extension publishing documentation.
- Spring Boot auto-configuration and starter documentation.
- jOOQ and QueryDSL documentation as structure and comparison references only.

## Release Path

Before `0.1.0-alpha.1`, R24 must still complete:

1. API/Javadocs readiness review.
2. Examples and first-user path readiness.
3. Packaging and publishing dry-runs without publication.
4. CI, repository health, and security readiness review.
5. Performance wording and retained evidence review.
6. Final go/no-go decision.

Publication, tagging, release notes, PR/merge work, and application migration
are outside R24.2/R24.3 and require a later explicit decision.
