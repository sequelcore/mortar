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
| R24.4 API and Javadocs readiness review | Done |
| R24.5 Examples and first-user path readiness | Done |
| R24.6 Packaging and publishing dry-runs | Done |
| R24.7 CI, repository health, and security readiness | Done |
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

## R24.4/R24.5 Readiness Decision

The API and examples readiness review remains a bounded pre-release hardening
step. It does not remove public APIs, add query families, generate
repositories, publish artifacts, or widen Mortar beyond the current
PostgreSQL/JDBC/Spring Boot scope.

Public API decision:

- Keep the current public Java surface. No class, package, or method removal is
  justified by the review.
- Improve Javadocs where they define external contracts: query construction,
  rendering, execution, generated read facades, cardinality, null handling,
  connection ownership, and failure modes.
- Avoid blanket comments on trivial bean accessors and enum constants.
- Keep generated fixed reads inspectable and rendered, not self-executing.
- Keep CLI help changes limited to existing commands and flags.

Examples and first-user decision:

- Keep `examples/spring-boot-postgres` as the quickest runnable Spring Boot
  path.
- Keep `examples/clean-architecture-postgres` as the DDD/Clean Architecture
  boundary example.
- Keep `examples/query-corpus-*` as public-facing corpus fixtures for query
  coverage, not as the first-user path.
- Remove roadmap slice identifiers from user-facing example prose while
  preserving them in roadmap and ADR history.
- Fix stale CLI documentation where the current command behavior already
  supports JSON output.

Risks accepted and mitigated:

- API redesign remains out of scope for this slice; only proven readiness fixes
  are allowed.
- Javadocs must describe behavior, boundaries, and exceptions, not restate
  method names.
- Generated-source documentation must be covered by processor tests so it does
  not drift silently.
- CLI documentation and `mortar --help` must stay aligned for the existing
  command set.

## R24.4/R24.5 Implementation Plan

R24.4 API and Javadocs readiness:

1. Tighten Javadocs on core query entry points, query builders, bound values,
   mutation/scalar contracts, and diagnostics where they affect public use.
2. Tighten Javadocs on PostgreSQL rendering and JDBC runtime execution
   boundaries, including connection ownership, explicit execution, cardinality,
   and exception behavior.
3. Tighten generated `Q*` Javadocs for the `Read` facade, fixed read members,
   generated query contracts, row records, and parameter records.
4. Verify generated-source Javadocs through processor tests.
5. Run the Java Javadoc task for publishable modules as the public API
   documentation readiness check.

R24.5 examples and first-user path readiness:

1. Keep the Spring Boot example focused on generated reads, DSL reads,
   scalar reads, mutations, `RETURNING`, JDBC execution, starter configuration,
   and SQL testkit assertions.
2. Keep the Clean Architecture example focused on pure application ports and
   PostgreSQL infrastructure adapters.
3. Align CLI docs and help text for existing commands.
4. Remove stale slice labels and deleted-reference residue from user-facing
   docs.
5. Re-run example checks and scrub docs for private paths, unsupported
   patterns, and non-public process wording.

## R24.4/R24.5 Completion Evidence

R24.4 API and Javadocs readiness outcome:

- Public Java API review covered core query construction and bound values,
  PostgreSQL rendering, JDBC execution, Spring Boot starter properties and
  diagnostics, testkit assertions, processor annotations, and generated `Q*`
  read facades.
- No accidental public API, dead public API, duplicate public surface, or
  justified public API removal was found.
- Javadocs were tightened where they define external contracts: rendering and
  execution boundaries, generated read facades, scalar and mutation contracts,
  connection ownership, cardinality, unsafe raw SQL, string/PostgreSQL
  predicate validation, and exception context.
- Generated-source Javadocs now state that generated reads are rendered and
  inspectable, not self-executing. Processor tests assert this generated
  wording.
- Java Javadoc generation succeeds for all publishable modules. Remaining
  Javadoc warnings are missing tag details on existing records and simple
  methods; they are not malformed-comment failures and do not block R24.4.

R24.5 examples and first-user path outcome:

- `examples/spring-boot-postgres` remains the quickest runnable first-user
  path and demonstrates generated fixed reads, DSL reads, `count`, `exists`,
  insert/update/delete mutations, PostgreSQL `RETURNING`, JDBC execution,
  starter configuration, and SQL testkit assertions.
- `examples/clean-architecture-postgres` remains the DDD/Clean Architecture
  example and keeps Mortar/JDBC/PostgreSQL concerns inside infrastructure
  adapters.
- `examples/query-corpus-*` remain public query-corpus fixtures, not the
  first-user path.
- CLI docs now match current JSON output behavior, and `mortar --help`
  describes the existing public commands without adding commands.
- User-facing example docs no longer use stale roadmap slice labels for
  current guidance.

Verification evidence:

- `gradlew.bat check --no-daemon`
- `gradlew.bat :java:core:javadoc :java:dialect-postgres:javadoc :java:runtime-jdbc:javadoc :java:spring-boot-starter:javadoc :java:processor:javadoc :java:testkit:javadoc --no-daemon`
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest :java:core:test :java:runtime-jdbc:test :java:testkit:test :examples:spring-boot-postgres:test :examples:clean-architecture-postgres:test --no-daemon`
- `cd rust && cargo fmt --all --check`
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`
- `cd rust && cargo test`
- `cd editors/vscode && bun run typecheck`
- `git diff --check`
- private path, non-public process wording, stale deleted-reference,
  wildcard-import, and local Markdown link scans
- public API review, DDD/Clean Architecture boundary review, and focused code
  review

Review outcome:

- Public API review found no accidental API expansion and no justified API
  removals.
- DDD/Clean Architecture review found no boundary drift.
- Focused code review found no blocking issues. Residual risks are documented
  below.

Residual risks:

- Some existing public records and simple methods still produce Javadoc
  missing-tag warnings. R24.4 improved semantic contract docs but avoided
  tag-only boilerplate.
- Historical R16/R17 identifiers remain in ADRs, roadmap history, tests, and
  fixture names where they identify durable history or test fixtures. They were
  removed from current first-user prose.

## R24.6/R24.7 Readiness Decision

R24.6 and R24.7 use a dry-run-only, fail-closed release-readiness posture.
Active workflows validate package shape and repository readiness without
publishing, tagging, creating releases, opening pull requests, merging, pushing,
or migrating an application.

Publishing remains a later decision. The previous tag-triggered publication
workflow was converted to a manual release-readiness workflow, and the Gradle
build now verifies that active workflows and Java publishing configuration do
not contain remote upload paths.

## R24.6/R24.7 Outcome

R24.6 packaging readiness:

- Java publishable modules remain limited to `java/core`,
  `java/dialect-postgres`, `java/runtime-jdbc`, `java/spring-boot-starter`,
  `java/processor`, and `java/testkit`; examples, benchmarks, aggregate
  projects, and editor plugins remain outside Maven publication.
- Java coordinates, generated POM metadata, source jars, Javadocs, Spring Boot
  starter metadata, annotation processor metadata, and Maven local publication
  were validated without Maven Central or GitHub Packages upload.
- GitHub Packages is not an active registry for R24 readiness. Maven Central
  remains the intended public Java registry unless a later release decision
  adds a separate GitHub Packages strategy.
- Rust package contents were inspected for `mortar-compiler`, `mortar-cli`, and
  `mortar-lsp`. The compiler crate dry-run passes; dependent crate dry-runs
  fail closed until the compiler crate exists in the target registry.
- VS Code packaging now produces a local VSIX through `vsce package` and does
  not publish to the Marketplace.

R24.7 CI, repository health, and security readiness:

- CI and release-readiness workflows use explicit minimal permissions.
- The active release-readiness workflow has no tag trigger, repository write
  permission, publishing secret fetch, Maven Central upload, crates.io upload,
  Marketplace publish, or GitHub release creation.
- Benchmark workflows remain manual and evidence-oriented.
- Dependabot monitors GitHub Actions, Gradle, Cargo, and VS Code npm
  dependencies.
- CodeQL runs for Java and TypeScript with job-scoped security upload
  permission.
- `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `SECURITY.md`, and
  `CODE_OF_CONDUCT.md` remain the public repository health surface. No
  placeholder issue templates or ownership files were added without a confirmed
  public owner path.

Accepted residual risks:

- VS Code packaging is not bundled yet, so `vsce package` warns about extension
  file count. The package remains valid for R24.6 dry-run evidence.
- Rust dependent crate publish dry-runs cannot fully verify registry dependency
  resolution until `mortar-compiler` is published or a later release process
  uses a registry staging strategy.
- Java, Rust, and VS Code version alignment remains a later R24.9 decision:
  Java is `0.1.0-alpha.1`, while Rust crates and the VS Code extension remain
  `0.1.0`.
- Branch protection, protected environments, publisher ownership, Central
  credentials, crates.io ownership, and Marketplace credentials are external
  release prerequisites and are not completed by R24.6/R24.7.

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

Additional R24.4/R24.5 research basis:

- Oracle Javadoc doc-comment guidance:
  https://www.oracle.com/java/technologies/javase/writing-doc-comments.html
- Oracle `javadoc` command documentation:
  https://docs.oracle.com/en/java/javase/24/docs/specs/man/javadoc.html
- Sonatype Central publishing requirements for Javadoc and source jars:
  https://central.sonatype.org/publish/requirements/
- Gradle Java plugin Javadoc, source jar, and Javadoc jar tasks:
  https://docs.gradle.org/current/userguide/java_plugin.html
- Spring Boot auto-configuration and starter guidance:
  https://docs.spring.io/spring-boot/3.5-SNAPSHOT/reference/features/developing-auto-configuration.html
- VS Code extension contribution points for commands and settings:
  https://code.visualstudio.com/api/references/contribution-points
- Cargo manifest metadata guidance:
  https://doc.rust-lang.org/cargo/reference/manifest.html
- jOOQ DSL API documentation, used as a public API documentation reference:
  https://www.jooq.org/doc/latest/manual/sql-building/dsl-api/
- QueryDSL reference documentation, used as a generated-query API
  documentation reference:
  https://querydsl.com/static/querydsl/latest/reference/html_single/

Additional R24.6/R24.7 research basis:

- Sonatype Central Portal publishing requirements:
  https://central.sonatype.org/publish/requirements/
- Gradle signing and publication guidance:
  https://docs.gradle.org/current/userguide/publishing_signing.html
- GitHub Packages Gradle registry guidance:
  https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-gradle-registry
- GitHub Actions secure-use guidance:
  https://docs.github.com/en/actions/reference/security/secure-use
- GitHub security features:
  https://docs.github.com/en/code-security/getting-started/github-security-features
- OpenSSF Scorecard checks:
  https://github.com/ossf/scorecard/blob/main/docs/checks.md
- Cargo manifest package metadata and include/exclude guidance:
  https://doc.rust-lang.org/cargo/reference/manifest.html
- Cargo publish dry-run guidance:
  https://doc.rust-lang.org/cargo/commands/cargo-publish.html
- VS Code extension packaging and publishing guidance:
  https://code.visualstudio.com/api/working-with-extensions/publishing-extension
- VS Code command contribution guidance:
  https://code.visualstudio.com/api/extension-guides/command
- Spring Boot auto-configuration and starter guidance:
  https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html

## Remaining R24 Work

R24.8-R24.9 remain Planned:

1. Performance wording and benchmark evidence review.
2. `0.1.0-alpha` go/no-go decision.

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
- documentation residue scan for non-public process wording
- link/reference sanity check
- documentation review for no archive/log clutter, no construction diary, no
  public overclaims, no broken references, R23 Done, R24 In Progress, and only
  R24.6/R24.7 newly Done in this change
