# R24 Public Documentation And Pre-Release Readiness Plan

Date: 2026-06-03
Status: Current R24 plan

This plan tracks the remaining work before Mortar can make a first public alpha
readiness decision. The canonical public status source is
[`roadmap.md`](roadmap.md).

R24 does not publish artifacts, create tags, open distribution channels, or
migrate an application. Publication steps happen only after a separate
readiness decision authorizes them.

## Current R24 Status

| Slice | Status |
| --- | --- |
| R24.1 Documentation canonization strategy and readiness plan | Done |
| R24.2 Public documentation canon audit | Done |
| R24.3 Public documentation rewrite and cleanup | Done |
| R24.4 API and Javadocs readiness review | Planned |
| R24.5 Examples and first-user path readiness | Planned |
| R24.6 Packaging and publishing dry-runs | Planned |
| R24.7 CI, repository health, and security readiness | Planned |
| R24.8 Performance wording and benchmark evidence review | Planned |
| R24.9 `0.1.0-alpha` go/no-go decision | Planned |

R23 remains Done. R24 remains In Progress until R24.9 closes.

## Canonical Documentation Ownership

| Document | Owner |
| --- | --- |
| `README.md` | Public entry point, supported scope, install preview, and documentation index. |
| `docs/getting-started.md` | First successful local user path. |
| `docs/usage-guide.md` | Query path selection, Spring wiring, testing, and diagnostics. |
| `docs/api-reference.md` | Handwritten public Java API overview; Javadocs own exact signatures. |
| `docs/spring-boot-postgres-example.md` | Runnable Spring/PostgreSQL example. |
| `docs/ddd-clean-architecture-example.md` | DDD/Clean Architecture placement guidance. |
| `docs/query-recipes.md` | Copyable repository authoring recipes. |
| `docs/refactor-safety.md` | Current refactor-safety contract and fail-closed behavior. |
| `docs/sql-snapshots.md` | Snapshot format and lifecycle. |
| `docs/testkit.md` | SQL, metadata, snapshot, and EXPLAIN assertions. |
| `docs/lsp.md` | Language Server Protocol behavior and limits. |
| `docs/vscode.md` | VS Code extension behavior and setup. |
| `docs/metadata.md` | Processor metadata and source-map formats. |
| `docs/performance.md` | Performance policy, retained evidence boundaries, and non-claims. |
| `docs/benchmarks/README.md` | Benchmark commands, retained evidence, and scenario catalog. |
| `docs/comparison.md` | Bounded comparison with related Java persistence tools. |
| `docs/troubleshooting.md` | Current user-facing setup and usage fixes. |
| `docs/release.md` | Versioning, artifacts, compatibility, and publication policy. |
| `docs/roadmap.md` | Short canonical roadmap and public status source. |
| `docs/plan.md` | Current R24 readiness plan and audit outcome. |
| `docs/adr/` | Durable architecture decisions only. |

Supporting docs:

- `docs/cli.md`
- `docs/diagnostics.md`
- `docs/editor-smoke-tests.md`
- `docs/intellij.md`
- `docs/migration-from-spring-data-query.md`
- `docs/neovim.md`
- `docs/spec/architecture.md`
- `docs/spring-boot-compatibility.md`
- `docs/examples/spring-clean-architecture-repository.md`
- `CHANGELOG.md`
- `CONTRIBUTING.md`
- `SECURITY.md`
- `CODE_OF_CONDUCT.md`

## R24.2 Audit Outcome

| File or group | Classification | Decision |
| --- | --- | --- |
| `README.md` | Canonical rewrite | Rewritten as the public first impression with supported scope and pre-release status. |
| `docs/roadmap.md` | Canonical rewrite | Reduced to short current status, completed capabilities, R24 plan, and release path. |
| `docs/plan.md` | Canonical rewrite | Kept as current R24 readiness plan with audit outcome and next slices. |
| `docs/getting-started.md` | Canonical rewrite | Kept; first-user flow remains current and concise. |
| `docs/usage-guide.md` | Canonical rewrite | Kept; stale slice language removed. |
| `docs/api-reference.md` | Canonical rewrite | Kept; current public API overview only. |
| `docs/spring-boot-postgres-example.md` | Canonical keep | Kept as runnable example guide. |
| `docs/ddd-clean-architecture-example.md` | Canonical rewrite | Kept; process references removed. |
| `docs/query-recipes.md` | Canonical rewrite | Kept; public repository recipes replace process-specific framing. |
| `docs/refactor-safety.md` | Canonical rewrite | Kept; current contract replaces phase diary wording. |
| `docs/sql-snapshots.md` | Canonical keep | Kept; owns snapshot lifecycle. |
| `docs/testkit.md` | Canonical keep | Kept; owns assertion APIs. |
| `docs/lsp.md` | Canonical rewrite | Kept; current LSP behavior replaces historical slice narrative. |
| `docs/vscode.md` | Canonical keep | Kept; owns VS Code client behavior. |
| `docs/metadata.md` | Canonical rewrite | Kept; current metadata/source-map format only. |
| `docs/performance.md` | Canonical rewrite | Kept; aligned to R23 retained evidence and no public overclaims. |
| `docs/benchmarks/README.md` | Canonical rewrite | Kept; old local/internal benchmark plan text removed. |
| `docs/comparison.md` | Canonical keep | Kept; bounded comparisons only. |
| `docs/troubleshooting.md` | Canonical keep | Kept; user-facing fixes. |
| `docs/release.md` | Canonical rewrite | Kept on the stable path and rewritten as the public release policy. |
| `docs/adr/*.md` | Canonical keep | Kept because these are real architecture decisions. Historical slice IDs remain decision context. |
| `docs/research/*.md` | Non-public internal/process residue | Deleted; useful performance constraints were synthesized into performance and benchmark docs. |
| `docs/r17-query-corpus.md` | Non-public internal/process residue | Deleted; fixture detail belongs in tests and git history. |
| `docs/performance-report-template.md` | Non-public internal/process residue | Deleted; public report criteria are now in performance and benchmark docs. |
| `docs/benchmarks/baseline-2026-06-01.md` | Non-public internal/process residue | Deleted; local-only baseline text is not public canon. |
| `docs/benchmarks/postgres-execution-2026-06-01.md` | Non-public internal/process residue | Deleted; local baseline text is not public canon. |
| `docs/benchmarks/performance-report-2026-06-01.md` | Non-public internal/process residue | Deleted; draft report is not public-ready. |
| `docs/benchmarks/r20-benchmark-readiness.md` | Non-public internal/process residue | Deleted; R20 planning detail is superseded by current benchmark policy. |
| `docs/benchmarks/r20-performance-gate.md` | Non-public internal/process residue | Deleted; outcome synthesized into current performance policy. |
| `docs/benchmarks/r23-retained-performance-evidence.md` | Merge into another doc | Deleted after R23 retained evidence summary and artifact links were moved into benchmark docs and roadmap. |
| `docs/benchmarks/r23-benchmark-readiness.md` | Merge into another doc | Deleted after decision summary was moved into benchmark docs and roadmap. |
| `docs/benchmarks/r23-performance-gate.md` | Merge into another doc | Deleted after optimization no-go and public-claim boundary were moved into benchmark docs and roadmap. |
| build, cache, dependency, generated, and target outputs | Generated/build output ignore | Ignored by audit and scrub checks. |

## R24.3 Cleanup Outcome

- Public docs now describe current behavior, supported scope, limits, and next
  milestones.
- Internal research notes, report templates, local benchmark baselines, and old
  decision-gate pages were deleted instead of archived.
- README no longer presents research notes or local reports as canonical docs.
- Performance docs state exactly what retained R23 evidence exists and what it
  does not prove.
- Release policy remains at `docs/release.md`, with current readiness wording.
- ADRs remain decision records, not logs.

## Architecture Review Outcome

The R24.2/R24.3 review accepted a compact public canon:

- Make `docs/roadmap.md` short, current, and forward-looking.
- Keep `docs/plan.md` as the current R24 readiness plan and audit record.
- Keep history in git, not archive folders or public construction diaries.
- Keep benchmark evidence public only at the level needed for supported
  non-claim wording and retained artifact traceability.
- Keep ADRs that record real architecture decisions.
- Use official publishing, package, security, and starter documentation as
  readiness criteria, not as proof that Mortar has released.

## Research Basis

- Sonatype Central Portal publishing and requirements:
  https://central.sonatype.org/publish/publish-portal-maven/
- Sonatype Central Portal Gradle guidance:
  https://central.sonatype.org/publish/publish-portal-gradle/
- Gradle Maven Publish plugin:
  https://docs.gradle.org/current/userguide/publishing_maven.html
- GitHub community profile documentation:
  https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-community-profiles-for-public-repositories
- GitHub Actions secure use guidance:
  https://docs.github.com/en/actions/reference/security/secure-use
- GitHub repository security quickstart:
  https://docs.github.com/en/code-security/getting-started/quickstart-for-securing-your-repository
- OpenSSF Scorecard:
  https://github.com/ossf/scorecard
- Cargo publishing:
  https://doc.rust-lang.org/cargo/reference/publishing.html
- VS Code extension publishing:
  https://code.visualstudio.com/api/working-with-extensions/publishing-extension
- Spring Boot auto-configuration and starter guidance:
  https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html
- jOOQ manual, used as a documentation-structure and comparison reference:
  https://www.jooq.org/doc/latest/manual/
- QueryDSL reference documentation, used as a comparison reference:
  https://querydsl.com/static/querydsl/latest/reference/html_single/

## Remaining R24 Work

R24.4-R24.9 remain Planned:

1. API and Javadocs readiness review.
2. Examples and first-user path readiness.
3. Packaging and publishing dry-runs.
4. CI, repository health, and security readiness.
5. Performance wording and benchmark evidence review.
6. `0.1.0-alpha` go/no-go decision.

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
- documentation residue scan for internal workflow language
- link/reference sanity check
- documentation review for no archive/log clutter, no construction diary, no
  public overclaims, no broken references, R23 Done, R24 In Progress, and only
  R24.2/R24.3 newly Done
