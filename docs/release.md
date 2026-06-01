# Release Policy

Mortar is pre-1.0.

## Versioning

Before `1.0.0`:

- minor versions may include breaking API changes;
- patch versions should be compatible bug fixes;
- every breaking change requires migration notes.

After `1.0.0`, Mortar follows semantic versioning:

- MAJOR for incompatible API changes;
- MINOR for backwards-compatible functionality;
- PATCH for backwards-compatible bug fixes.

## Release Checklist

1. `docs/roadmap.md` is current.
2. `CHANGELOG.md` contains user-visible changes.
3. Public API changes have migration notes.
4. Architecture changes have ADRs.
5. Java gates pass: `./gradlew check`.
6. Rust gates pass: `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, `cargo test`.
7. Examples compile once examples exist.
8. Maven Central dry run passes with `./gradlew publishToMavenLocal --no-configuration-cache`.
9. Rust publish dry run passes for crates that have all registry dependencies available.
10. GitHub release notes are drafted from `CHANGELOG.md`.

## Artifact Policy

Java artifacts use group `io.github.sequelcore`.

Public Java artifact IDs:

- `mortar-core`
- `mortar-dialect-postgres`
- `mortar-runtime-jdbc`
- `mortar-spring-boot-starter`
- `mortar-processor`
- `mortar-testkit`

Rust crates use the `mortar-*` naming convention.

Generated artifacts must include license metadata and source links.

## Maven Central Policy

Publish only public Java libraries:

- `java:core`
- `java:dialect-postgres`
- `java:runtime-jdbc`
- `java:spring-boot-starter`
- `java:processor`
- `java:testkit`

Do not publish examples, benchmarks, aggregate projects, or editor plugins to
Maven Central.

Local dry run:

```bash
./gradlew publishToMavenLocal --no-configuration-cache
```

Signed Maven Central publishing is handled by `.github/workflows/publish.yml`
on version tags. The workflow follows the Vigil release pattern and fetches
Central Portal and GPG credentials from Doppler project `sequel-core`, config
`prd`.

Required injected environment variables:

- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`
- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`

The workflow publishes with:

```bash
./gradlew publishToMavenCentral --no-daemon --no-configuration-cache
```

Local dry runs do not require signing credentials. Release publishing signs
artifacts when `ORG_GRADLE_PROJECT_signingInMemoryKey` is present in CI.

## GitHub Release Policy

Every GitHub release must include:

- version tag;
- changelog excerpt;
- migration notes for breaking changes;
- links to Maven and crates.io artifacts when published;
- checksums for manually attached binaries, if any.

## Rust Crate Policy

Publish Rust crates in dependency order:

1. `mortar-compiler`
2. `mortar-cli`
3. `mortar-lsp`

Before the first public release, CI dry-runs `mortar-compiler` because dependent
crates cannot complete `cargo publish --dry-run` until `mortar-compiler` exists
in the registry. Dependent crates keep versioned path dependencies so they are
ready to publish after the compiler crate is available.

Local dry run:

```bash
cd rust
cargo publish --dry-run -p mortar-compiler
```
