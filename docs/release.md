# Release Policy

Mortar is pre-1.0 and has not published a public release.

R24 recorded a conditional go for the `0.1.0-alpha` publication decision. R25
adds guarded release automation for a later explicit alpha publication action.
No artifact has been published yet.

## Versioning

Before `1.0.0`:

- minor versions may include breaking API changes;
- patch versions should be compatible bug fixes;
- every breaking change requires migration notes that identify the affected
  artifact, package, type or property, replacement path, and automation status.

After `1.0.0`, Mortar follows semantic versioning:

- MAJOR for incompatible API changes;
- MINOR for backwards-compatible functionality;
- PATCH for backwards-compatible bug fixes.

## Public Compatibility Surface

Before `1.0.0`, Mortar treats these as public compatibility surfaces:

- Maven coordinates listed in this policy;
- handwritten Java public types in `dev.mortar.core`, `dev.mortar.jdbc`,
  `dev.mortar.postgres`, `dev.mortar.spring`, `dev.mortar.processor`, and
  `dev.mortar.testkit`;
- annotation names and annotation attributes used by the processor;
- generated `Q*` naming conventions, generated table/column fields, generated
  `findAll(renderer)` and `findById(renderer)` read facades, generated
  parameter records, and generated row records;
- stable processor and core diagnostic codes;
- Spring Boot starter properties under `mortar.*`;
- Rust CLI command names and documented snapshot/report file formats.

Pre-`1.0` minor releases may still change these surfaces, but every breaking
change must be listed in `CHANGELOG.md` and linked from release notes.

Current tested compatibility envelope:

- Java 21;
- Spring Boot 3.5.x for the starter;
- PostgreSQL 16 for PostgreSQL Testcontainers evidence;
- PostgreSQL as the only supported starter dialect;
- Rust stable toolchain for CLI/compiler/LSP crates.

Untested or future compatibility must not be described as supported in release
notes, README, Maven metadata, or crate metadata.

## Artifact Policy

Java artifacts use group `io.github.sequelcore`.

Public Java artifact IDs:

- `mortar-core`
- `mortar-dialect-postgres`
- `mortar-runtime-jdbc`
- `mortar-spring-boot-starter`
- `mortar-processor`
- `mortar-testkit`

Publish only public Java libraries to Maven Central:

- `java:core`
- `java:dialect-postgres`
- `java:runtime-jdbc`
- `java:spring-boot-starter`
- `java:processor`
- `java:testkit`

Do not publish examples, benchmarks, aggregate projects, or editor plugins to
Maven Central.

Rust crates use the `mortar-*` naming convention and publish in dependency
order:

1. `mortar-compiler`
2. `mortar-cli`
3. `mortar-lsp`

## Release Readiness Checklist

Before a public release:

1. `docs/roadmap.md` is current.
2. `CHANGELOG.md` contains user-visible changes.
3. Public API changes have migration notes.
4. Architecture changes have ADRs.
5. Java gates pass with `gradlew.bat check --no-daemon`.
6. Rust gates pass with `cargo fmt`, `cargo clippy`, and `cargo test`.
7. VS Code typecheck passes when the extension is included.
8. Examples compile and tests pass.
9. Maven local publication evidence exists.
10. Central Portal validation or publication dry-run evidence exists for
    Java artifacts before any Maven Central publication.
11. Cargo package inspection and dry-run evidence exists for publishable
    crates.
12. Dependent Rust crates that cannot complete a crates.io publish dry-run
    until their internal dependencies are published have package-inspection
    evidence and a documented fail-closed reason.
13. VS Code package dry-run evidence exists if the extension is included.
14. Release automation uses scoped permissions, manual dispatch, protected
    environment gates, explicit confirmation, and runtime secret fetch before
    any upload step.
15. Release notes are drafted from `CHANGELOG.md`.

## Dry-Run Commands

Maven local publication:

```bash
gradlew.bat publishToMavenLocal --no-daemon --no-configuration-cache
```

Cargo package inspection and dry run:

```bash
cd rust
cargo package --list -p mortar-compiler
cargo package --list -p mortar-cli
cargo package --list -p mortar-lsp
cargo publish --dry-run -p mortar-compiler
```

`mortar-cli` and `mortar-lsp` depend on `mortar-compiler`. Before the compiler
crate is available in the target registry, their `cargo publish --dry-run`
checks may fail closed during registry dependency resolution. Package contents
must still be inspected before any release decision.

VS Code extension check:

```bash
cd editors/vscode
bun run typecheck
bun run package:vsix
```

`package:vsix` creates a local VSIX through the VS Code `vsce package` path. It
does not publish to the VS Code Marketplace.

## Publication Policy

Release automation is manual and guarded. The default workflow operation is
`validate`, which checks the release candidate without fetching publisher
credentials or uploading artifacts. Registry uploads require a separate
`operation=publish` workflow dispatch.

Publishing requires:

- `release_ref` equal to `v<release_version>`;
- `confirmation` equal to `publish <release_version>`;
- at least one selected artifact family;
- the protected GitHub `release` environment;
- a repository `DOPPLER_TOKEN` secret that can fetch release credentials from
  Doppler at runtime.

The workflow does not publish on pushes, branch updates, pull requests, or tag
creation. It does not create GitHub releases.

Maven Central publishing must use the current Central Portal path, validated
POM metadata, sources, Javadocs, license metadata, SCM metadata, developer
metadata, signatures, and CI-provided credentials.

Cargo publishing must inspect package contents before upload because published
crate versions are permanent and cannot be overwritten. Mortar publishes Rust
crates in dependency order: `mortar-compiler`, then `mortar-cli`, then
`mortar-lsp`.

VS Code extension publication requires the `sequelcore` Marketplace publisher,
a Marketplace PAT, and packaging evidence. The current workflow publishes with
the VS Code pre-release flag when the VS Code artifact family is selected.

Before an alpha publication action, complete or verify these external
prerequisites:

1. Confirm Sonatype Central namespace ownership for `io.github.sequelcore`,
   Central Portal token access, and signing credentials.
2. Confirm crates.io owner access for `mortar-compiler`, `mortar-cli`, and
   `mortar-lsp`, and publish in dependency order.
3. Confirm VS Code Marketplace publisher ownership for `sequelcore` and a
   Marketplace PAT if the extension is included.
4. Add or verify protected release controls for publication credentials,
   including branch protection or rulesets and a protected environment or
   equivalent approval gate.
5. Run the release workflow in `validate` mode for the exact release tag before
   any `publish` operation.

Required Doppler release secrets:

- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`
- `CARGO_REGISTRY_TOKEN`
- `VSCE_PAT`

The GitHub repository should store only the Doppler service token needed by the
release workflow. Do not copy registry tokens, signing keys, or Marketplace PAT
values into repository secrets, docs, logs, or prompts.

No release, tag, Maven Central publication, crates.io publication, VS Code
Marketplace publication, GitHub release, PR, merge, or application migration is
authorized by R25.1 automation work alone.
