# R25 Formal Release Automation Plan

Date: 2026-06-03
Status: Current R25 plan

This plan tracks the work that turns the R24 conditional alpha decision into a
guarded release path. The canonical public status source remains
[`roadmap.md`](roadmap.md).

R25 does not publish artifacts by itself. Publication still requires an
explicit workflow dispatch, a protected release environment, release secrets,
version alignment, and confirmation text for the exact version being
published.

## Current R25 Status

| Slice | Status |
| --- | --- |
| R25.1 Guarded release automation and documentation | Done |
| R25.2 `0.1.0-alpha.1` publication action | Planned |

R24 remains Done. R25 is In Progress until a publication action is either
completed or explicitly rejected.

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

1. `mortar-compiler`
2. `mortar-cli`
3. `mortar-lsp`

VS Code publication packages the bundled VSIX and publishes through `vsce` as a
pre-release using the `sequelcore` Marketplace publisher.

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
- `cd rust && cargo publish --dry-run -p mortar-compiler`
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

R25.2 is the only remaining R25 slice. It should:

1. verify the GitHub `release` environment and external publisher access;
2. create or confirm the `v0.1.0-alpha.1` release tag;
3. run the release workflow in `validate` mode on the tag;
4. if the validation run passes, dispatch `operation=publish` for the selected
   artifact families;
5. verify Maven Central, crates.io, and VS Code Marketplace results;
6. update `CHANGELOG.md`, `docs/release.md`, and `docs/roadmap.md` with the
   exact publication outcome.

No publication is complete until the registries show the expected artifacts.
