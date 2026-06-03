# Release Policy

Mortar is pre-1.0 and has not published a public release.

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
10. Central Portal validation or publish-workflow dry-run evidence exists for
    Java artifacts before any Maven Central publication.
11. Cargo package inspection and dry-run evidence exists for publishable
    crates.
12. VS Code package dry-run evidence exists if the extension is included.
13. GitHub Actions publish workflow uses scoped permissions, protected secrets,
    and dry-run jobs before publication jobs.
14. Release notes are drafted from `CHANGELOG.md`.

## Dry-Run Commands

Maven local publication:

```bash
gradlew.bat publishToMavenLocal --no-daemon --no-configuration-cache
```

Cargo package inspection and dry run:

```bash
cd rust
cargo package --list -p mortar-compiler
cargo publish --dry-run -p mortar-compiler
```

VS Code extension check:

```bash
cd editors/vscode
bun run typecheck
```

This command is not a VSIX packaging dry run. A real package dry run, normally
through the VS Code `vsce package` path, must be added and verified during
R24.6 if the VS Code extension is included in a release readiness decision.

## Publication Policy

Publication is tag-gated and requires an explicit go/no-go decision. Dry-runs do
not publish artifacts.

Maven Central publishing must use the current Central Portal path, validated
POM metadata, sources, Javadocs, license metadata, SCM metadata, developer
metadata, signatures, and CI-provided credentials.

Cargo publishing must inspect package contents before upload because published
crate versions are permanent and cannot be overwritten.

VS Code extension publication requires Marketplace publisher credentials and
packaging constraints for README/CHANGELOG assets. Packaging a VSIX is not the
same as publishing it.

No release, tag, Maven Central publication, crates.io publication, VS Code
Marketplace publication, GitHub release, PR, merge, or application migration is
authorized by R24.2/R24.3 documentation cleanup.
