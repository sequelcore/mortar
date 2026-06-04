# R25 Formal Release Automation Plan

Date: 2026-06-04
Status: R25 complete

This plan records the R25 release automation and first alpha publication
outcome. The canonical public status source remains [`roadmap.md`](roadmap.md).

R25 published the first alpha artifacts. Future release work should start from
this document, `CHANGELOG.md`, and [`release.md`](release.md).

## Current R25 Status

| Slice | Status |
| --- | --- |
| R25.1 Guarded release automation and documentation | Done |
| R25.2 `0.1.0-alpha.1` publication action | Done |

R24 and R25 are Done.

## R25.1 Outcome

R25.1 adds a guarded release workflow for the three publishable artifact
families:

- Java artifacts to Maven Central;
- Rust crates to crates.io;
- VS Code extension to the Marketplace pre-release channel.

The workflow remains manual. `operation=validate` is the default and does not
fetch release secrets or publish artifacts. `operation=publish` requires:

- `release_ref` equal to `v<release_version>`;
- `confirmation` equal to `publish <release_version>`;
- at least one artifact family flag;
- the protected `release` GitHub environment;
- a repository `DOPPLER_TOKEN` secret that can fetch release credentials from
  Doppler project `sequel-releases`, config `prd`, at runtime.

Java publication uses the Vanniktech Maven Publish plugin with Central Portal
publication enabled and automatic release configured. Maven Central credentials
and signing material are provided only through Doppler-injected environment
variables during the guarded publish job.

Rust publication uses Cargo in dependency order:

1. `sequel-mortar-compiler`
2. `sequel-mortar-cli`
3. `sequel-mortar-lsp`

VS Code publication packages the bundled VSIX and publishes through `vsce` as a
pre-release using the `sequelcore` Marketplace publisher.

## R25.2 Outcome

R25.2 published the first alpha artifacts:

- Java modules published to Maven Central as `0.1.0-alpha.1`.
- Rust crates published to crates.io as `sequel-mortar-compiler`,
  `sequel-mortar-cli`, and `sequel-mortar-lsp` version `0.1.0`.
- VS Code extension published to the Visual Studio Marketplace as
  `sequelcore.mortar-vscode` version `0.1.0` with the pre-release flag.

Java and VS Code publication used the guarded GitHub release workflow. Rust
publication was completed from the corrected `main` commit after the original
`mortar-*` crate names were rejected because crates.io treats hyphen and
underscore names as equivalent and pre-existing `mortar_*` crates already
occupied that namespace.

## Required Release Secrets

The GitHub repository stores only the Doppler service token:

- `DOPPLER_TOKEN`

The Doppler `sequel-releases/prd` config owns publisher credentials:

- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`
- `CARGO_REGISTRY_TOKEN`
- `VSCE_PAT`

Secret values must never be committed, logged, copied into docs, or passed to
agents. Workflow steps validate presence only.

## Release Controls

The `release` GitHub environment must be configured before any publish run.
Recommended controls:

- required reviewer or equivalent approval gate;
- restricted deployment branches/tags for `v*` release tags;
- no self-approval if GitHub plan support is available;
- repository branch protection or rulesets for `main` and release tags.

R25.1 adds the workflow path. R25.2 must verify the external GitHub
environment settings before dispatching `operation=publish`.

## Verification Requirements

R25 release automation changes require:

- `gradlew.bat check --no-daemon`
- `gradlew.bat publishToMavenLocal --no-daemon --no-configuration-cache`
- `cd rust && cargo fmt --all --check`
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`
- `cd rust && cargo test`
- `cd rust && cargo publish --dry-run -p sequel-mortar-compiler`
- `cd editors/vscode && bun run typecheck`
- `cd editors/vscode && bun run package:vsix`
- `git diff --check`
- private path and secret-value scrub excluding build, cache, dependency,
  generated, and target outputs
- workflow review for manual-only triggers, read-only repository permissions,
  protected environment usage, explicit confirmation, and no direct registry
  secrets in GitHub Actions YAML

## Research Basis

- Sonatype Central Portal and Vanniktech Maven Publish documentation for Maven
  Central publication and automatic release configuration.
- Gradle Maven Publish and signing documentation for local publication,
  metadata, sources, Javadocs, and signing inputs.
- Cargo publishing documentation for package inspection, dry-runs, token use,
  and crates.io publication order.
- VS Code Marketplace and `vsce` documentation for publisher credentials,
  package creation, and pre-release publication.
- GitHub Actions workflow syntax, permissions, and environment protection
  documentation for manual dispatch, least-privilege permissions, and guarded
  deployment jobs.
- Doppler GitHub Actions documentation for runtime secret fetch and environment
  injection.

## Remaining Work

No R25 release work remains. Follow-up work should focus on post-alpha
feedback, release-note polish for future versions, dependency updates, VS Code
bundling/file-count reduction, and next-scope planning.
