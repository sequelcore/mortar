# R24 Public Documentation And Pre-Release Readiness Plan

Date: 2026-06-03
Status: Current R24 plan

This plan defines the work needed before Mortar can make a first public alpha
readiness decision. The canonical roadmap and durable status record is
[`roadmap.md`](roadmap.md).

Mortar is public Java-first, refactor-safe, SQL-transparent query tooling for
Spring and PostgreSQL. R24 must keep that identity clear, current, and
verifiable.

## Scope

R24 covers public documentation canonization, API and Javadocs readiness,
examples, packaging dry-runs, repository health, CI hardening, performance
wording, and the final `0.1.0-alpha` readiness decision.

R24 does not publish artifacts, create version tags, open distribution channels,
or migrate an application. Publication steps happen only after a separate
readiness decision authorizes them.

## Canonical Documentation Ownership

| Document | Ownership |
| --- | --- |
| `README.md` | Public entry point, install preview, and docs index. |
| `docs/getting-started.md` | First successful local user path. |
| `docs/usage-guide.md` | Current query authoring and repository usage. |
| `docs/query-recipes.md` | AI-friendly and human-friendly query examples. |
| `docs/refactor-safety.md` | Refactor-safety contract and supported evidence. |
| `docs/sql-snapshots.md` | Snapshot format and lifecycle. |
| `docs/spring-boot-postgres-example.md` | Runnable Spring/PostgreSQL example. |
| `docs/spring-boot-compatibility.md` | Supported Spring Boot versions and limits. |
| `docs/testkit.md` | SQL, metadata, snapshot, and EXPLAIN assertions. |
| `docs/performance.md` | Measurement discipline and claim limits. |
| `docs/benchmarks/README.md` | Benchmark commands, retained evidence rules, and scenario catalog. |
| `docs/release.md` | Versioning, compatibility, artifacts, and publication policy. |
| `docs/api-reference.md` | Handwritten public Java API surface. |
| `docs/troubleshooting.md` | Current user-facing fixes for common failures. |
| `docs/comparison.md` | Bounded comparison with JPA Criteria, QueryDSL, jOOQ, Spring Data `@Query`, and Hibernate ORM. |
| `docs/roadmap.md` | Canonical roadmap, status, and durable evidence record. |
| `docs/plan.md` | This current R24 plan only. |
| `docs/adr/` | Durable architecture decisions only. |

Historical construction details should be removed, synthesized into current
topical docs, or left to git history. R24 must not introduce archive, history,
engineering-log, or similar folders.

R24.2 should decide whether supported scope and explicit exclusions belong in a
standalone `docs/limitations.md` page or stay inside the usage, comparison,
performance, and release-policy docs.

## Documentation Canonization Rules

- Public docs describe current behavior, contracts, usage, limits, and next
  milestones.
- Public docs avoid internal process language and temporary work notes.
- Repeated status narratives are removed from topical docs and kept in the
  roadmap when they are still useful.
- ADRs remain only when they explain real architectural decisions.
- Benchmark drafts must not read as public performance reports unless retained
  evidence supports every claim.
- Local paths, usernames, workstation details, raw build-output paths, and
  cache paths are not public documentation evidence.

## R24 Roadmap Slices

### R24.1 Documentation Canonization Strategy And Readiness Plan

Status: Done.

Outputs:

- R24 roadmap section rewritten as a current public readiness program.
- `docs/plan.md` reduced to the active R24 plan.
- Architecture review recorded below.
- ADR not added because the current decision follows existing project
  governance.

### R24.2 Public Documentation Canon Audit

Status: Planned.

Review every public entry point and classify it as canonical, supporting,
research, retained evidence, or candidate for deletion. Check `README.md`,
topical docs, benchmark docs, ADRs, release policy, security policy,
contribution guide, and changelog.

Exit criteria:

- Canonical docs index is accurate.
- Dead or duplicated docs are listed for removal or synthesis.
- Links into removed historical sections are replaced.
- No archive or log replacement structure is created.

### R24.3 Public Documentation Rewrite And Cleanup

Status: Planned.

Rewrite docs so first-time readers see Mortar's current state without reading
older roadmap construction records.

Exit criteria:

- README is concise and points to the right first-user path.
- Usage guide, query recipes, Spring Boot guide, testkit guide, SQL snapshots,
  refactor safety, API reference, comparison, troubleshooting, and limitations
  are current-state focused.
- Internal process residue is removed from public-facing docs.
- Historical construction details are deleted or synthesized into durable
  canonical docs.

### R24.4 API And Javadocs Readiness Review

Status: Planned.

Review handwritten public Java APIs, generated-source Javadocs, package names,
exception messages, diagnostics, nullability expectations, and examples.

Exit criteria:

- Public API names match Mortar's Java-first, SQL-transparent identity.
- Javadocs explain contracts and limits without overpromising stability.
- Generated APIs do not look larger or more stable than intended.
- API reference matches actual Java source.

### R24.5 Examples And First-User Path Readiness

Status: Planned.

Verify the runnable examples and docs path a new Java/Spring user would follow.

Exit criteria:

- First-user path starts from README and reaches a working Spring/PostgreSQL
  example.
- Clean Architecture example shows Mortar inside infrastructure adapters.
- Troubleshooting covers common setup, metadata, snapshot, and editor failures.
- Examples compile and test through normal verification.

### R24.6 Packaging And Publishing Dry-Run Readiness

Status: Planned.

Verify packaging posture without publishing. Cover Maven Central, Gradle local
publication, GitHub Packages strategy, Cargo package dry-runs where applicable,
VS Code extension packaging where applicable, and CI workflow behavior.

Exit criteria:

- Maven coordinates, POM metadata, sources, Javadocs, licenses, SCM, developer
  metadata, and signing behavior are verified.
- GitHub Packages is either documented as a secondary registry or explicitly
  deferred to avoid confusing the public install path.
- Cargo dry-runs verify package contents and crate metadata for publishable Rust
  crates.
- VS Code package dry-run verifies extension manifest, README/CHANGELOG asset
  constraints, and pre-release packaging behavior if the extension is included.
- CI uses scoped permissions and dry-run jobs before publication jobs.

### R24.7 CI, Repository Health, And Security Readiness

Status: Planned.

Review repository health files and security posture.

Exit criteria:

- `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`,
  `CODE_OF_CONDUCT.md`, issue templates, and changelog are current.
- Branch protection and required checks are documented for the readiness
  decision.
- Code scanning, dependency alerts, secret scanning posture, and artifact
  retention caveats are reviewed.
- Security policy tells users how to report vulnerabilities and which versions
  are supported.

### R24.8 Performance Wording And Benchmark Evidence Review

Status: Planned.

Review performance docs, benchmark docs, retained evidence, and public wording.

Exit criteria:

- Mortar makes no broad performance superiority claim.
- Permitted wording is limited to: "Mortar keeps benchmark discipline for
  supported scenarios; public performance claims require retained raw
  artifacts, disclosed methodology, exact baselines, and readiness review."
- Java runtime, Rust tooling, and editor latency evidence stay separate.
- Local smoke output and build-directory JSON are not used as public evidence.

### R24.9 `0.1.0-alpha` Go/No-Go Decision

Status: Planned.

Produce a readiness decision and blocker list after R24.2-R24.8 close.

Exit criteria:

- All verification gates pass on the target commit.
- Public docs are coherent and current-state focused.
- Packaging dry-runs have retained evidence.
- Repository health checks are acceptable for a public alpha.
- Remaining blockers are either resolved or explicitly accepted for alpha.

## Architecture Review Outcome

R24.1 architecture review accepted a narrow documentation governance approach:

- `docs/roadmap.md` remains the only durable roadmap and status source.
- `docs/plan.md` is the current R24 plan only.
- Durable decision records stay in ADRs only when an architectural decision
  exists.
- Git history remains the history; no archive or log folders are needed.
- GitHub Packages stays a strategy question unless R24.6 decides to document it
  as a supported secondary registry.
- R23 remains Done. R24 remains open until the readiness decision is complete.

An ADR is not needed for R24.1 because the decision follows the existing
canonical roadmap rule. Add an ADR later only if Mortar adopts a standing
documentation-governance policy that constrains future planning artifacts.

## Research Basis

- Sonatype Central Publisher Portal and requirements:
  https://central.sonatype.org/
- Gradle Maven Publish plugin:
  https://docs.gradle.org/current/userguide/publishing_maven.html
- GitHub Actions workflow permissions:
  https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax
- GitHub Packages Gradle registry:
  https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry
- GitHub repository security quickstart:
  https://docs.github.com/en/code-security/getting-started/quickstart-for-securing-your-repository
- GitHub code scanning:
  https://docs.github.com/en/code-security/concepts/code-scanning/about-code-scanning
- OpenSSF Scorecard:
  https://github.com/ossf/scorecard
- OpenSSF OSPS Baseline:
  https://openssf.org/wp-content/uploads/2026/01/OpenSSF-OSPS-Baseline.pdf
- Cargo publishing:
  https://doc.rust-lang.org/cargo/reference/publishing.html
- VS Code extension publishing:
  https://code.visualstudio.com/api/working-with-extensions/publishing-extension
- Spring Boot auto-configuration and starter guidance:
  https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- jOOQ manual:
  https://www.jooq.org/doc/latest/manual
- QueryDSL reference documentation:
  https://querydsl.com/static/querydsl/latest/reference/html_single/

## Verification Plan

R24 documentation changes require:

- `gradlew.bat check --no-daemon`
- `cd rust && cargo fmt --all --check`
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`
- `cd rust && cargo test`
- `cd editors/vscode && bun run typecheck`
- `git diff --check`
- private path, user, and project scrub excluding build, cache, dependency,
  generated, and target outputs
- documentation review for internal process residue, archive/log clutter, public
  overclaims, R23 Done status, and R24 not Done status
