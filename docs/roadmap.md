# Mortar Roadmap

Date: 2026-06-03
Status: Canonical

Mortar is Java-first, refactor-safe, SQL-transparent query authoring for Spring
and PostgreSQL. This roadmap records current public status and near-term
readiness work. Git history remains the detailed project history.

## Current Status

Mortar is pre-release. No public artifact has been published, no public release
tag has been created, and the first alpha remains gated by external publication
prerequisites.

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
- Performance claims are limited to retained benchmark evidence, disciplined
  measurement, and evidence boundaries; Mortar does not claim broad superiority
  over JDBC, PostgreSQL, jOOQ, QueryDSL, Hibernate, or Spring Data.
- Publication does not begin until external publisher ownership, credentials,
  protected release controls, and a later explicit release action are in place.

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
| R24 Public documentation and pre-release readiness | Done | Public documentation, API/Javadocs, examples, packaging dry-runs, CI/security posture, performance wording, benchmark evidence boundaries, and the first alpha publication decision are complete. |

R23 retained evidence covers the reviewed Java runtime, Rust tooling/LSP, and
VS Code editor-latency families. The durable evidence reference is the retained
evidence package structure, not temporary CI run links. R23 did not authorize
public performance superiority claims, product optimization, or deployment
performance guarantees.

## R24: Public Documentation And Pre-Release Readiness

Status: Done

Purpose: prepare Mortar for a first public alpha readiness decision. R24 is a
readiness program, not an artifact publication step.

| Slice | Status | Outcome |
| --- | --- | --- |
| R24.1 Documentation canonization strategy and readiness plan | Done | Public documentation ownership and readiness criteria defined. |
| R24.2 Public documentation canon audit | Done | Public docs classified; obsolete internal research, baseline, report, and planning residue identified for deletion or synthesis. |
| R24.3 Public documentation rewrite and cleanup | Done | README, roadmap, plan, performance docs, benchmark docs, release policy, and topical guides rewritten for current public scope. |
| R24.4 API and Javadocs readiness review | Done | Public API surfaces reviewed; targeted Javadocs and generated-source docs tightened without API expansion. |
| R24.5 Examples and first-user path readiness | Done | Spring Boot, Clean Architecture, and query-corpus example paths verified and user-facing stale guidance removed. |
| R24.6 Packaging and publishing dry-runs | Done | Verified Java Maven local publication, generated POM/source/Javadoc artifacts, Rust package inspection and dry-run behavior, VS Code VSIX packaging, and validation-only publication automation behavior without publishing. |
| R24.7 CI, repository health, and security readiness | Done | Hardened readiness automation to explicit minimal permissions, added dependency and CodeQL security automation, reviewed health files and security policy, and kept actual publication disabled. |
| R24.8 Performance wording and benchmark evidence review | Done | Public performance wording is limited to retained benchmark evidence, disciplined measurement, and explicit non-claims; temporary CI run links were removed from durable public docs. |
| R24.9 `0.1.0-alpha` go/no-go decision | Done | Conditional go for the alpha publication decision: package/readiness gates pass, and publication remains blocked until external publisher ownership, credentials, protected release controls, and an explicit release action are completed. |

R24 closes with conditional go, not full go and not no-go. Repository-side
package and documentation readiness are sufficient to close R24; actual
publication remains blocked by external ownership, credential, protected
release-control, and guarded automation prerequisites.

## R24.2/R24.3 Documentation Outcome

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

R24 closes with a conditional go for the `0.1.0-alpha` publication decision.
This means the repository is ready for a later alpha publication action after
the external prerequisites below are completed:

1. Sonatype Central namespace access for `io.github.sequelcore`, Central Portal
   token, and signing credentials.
2. crates.io ownership and publish order for `mortar-compiler`,
   `mortar-cli`, and `mortar-lsp`.
3. VS Code Marketplace publisher ownership for `sequelcore` and a Marketplace PAT.
4. Protected release environment or equivalent guarded secret access, branch
   protection/rulesets, and a release automation change that restores guarded
   publication steps.

Publication, tagging, release notes, PR/merge work, and application migration
remain outside R24 and require a later explicit release action.
