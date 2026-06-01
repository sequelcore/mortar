# Mortar

Mortar is a public Java + Rust project for Java-first, refactor-safe, SQL-transparent queries.

The goal is not to hide SQL. The goal is to let Java/Spring developers write queries as code while keeping the generated SQL visible, testable, auditable, and performance-aware.

## Principles

- Java-first API.
- SQL transparency as a product feature.
- Compile-time safety where Java can provide it.
- No ORM-style hidden lazy loading or invisible query behavior.
- Clean Architecture boundaries from day one.
- PostgreSQL first, with additional dialects only after concrete evidence.
- Benchmarks and tests are part of the contract.

## Structure

```text
java/core                  Pure query model and DSL contracts
java/processor             Annotation processor for generated metamodels and findById executors
java/dialect-postgres      PostgreSQL renderer
java/runtime-jdbc          JDBC execution adapter
java/spring-boot-starter   Spring Boot integration
java/testkit               Assertions and snapshot helpers
editors/vscode             VS Code client for the Mortar LSP server
editors/intellij           IntelliJ plugin adapter
rust/crates/mortar-cli     CLI tooling
rust/crates/mortar-compiler Rust analysis/compiler foundation
rust/crates/mortar-lsp     Language Server Protocol foundation for editor tooling
examples/spring-boot-postgres Runnable Spring Boot PostgreSQL example
```

## Canonical Docs

- [Getting started](docs/getting-started.md)
- [Roadmap](docs/roadmap.md)
- [Architecture](docs/spec/architecture.md)
- [API reference](docs/api-reference.md)
- [Spring Boot PostgreSQL example](docs/spring-boot-postgres-example.md)
- [DDD/Clean Architecture example](docs/ddd-clean-architecture-example.md)
- [Migration from Spring Data @Query](docs/migration-from-spring-data-query.md)
- [Comparison](docs/comparison.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Testkit](docs/testkit.md)
- [SQL snapshots](docs/sql-snapshots.md)
- [CLI](docs/cli.md)
- [LSP](docs/lsp.md)
- [VS Code](docs/vscode.md)
- [IntelliJ](docs/intellij.md)
- [Neovim](docs/neovim.md)
- [Editor smoke tests](docs/editor-smoke-tests.md)
- [Build metadata](docs/metadata.md)
- [Static diagnostics](docs/diagnostics.md)
- [Performance guide](docs/performance.md)
- [Performance report template](docs/performance-report-template.md)
- [PostgreSQL performance report](docs/benchmarks/performance-report-2026-06-01.md)
- [Benchmarks](docs/benchmarks/README.md)
- [ADR index](docs/adr/index.md)
- [ADR-0001](docs/adr/0001-java-first-sql-transparent-architecture.md)
- [Market notes](docs/research/market-notes.md)
- [Release policy](docs/release.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)

## Install

Mortar is pre-release. The first planned Maven Central version is
`0.1.0-alpha.1`.

```kotlin
dependencies {
    implementation("io.github.sequelcore:mortar-core:0.1.0-alpha.1")
    implementation("io.github.sequelcore:mortar-dialect-postgres:0.1.0-alpha.1")
    implementation("io.github.sequelcore:mortar-runtime-jdbc:0.1.0-alpha.1")
    implementation("io.github.sequelcore:mortar-spring-boot-starter:0.1.0-alpha.1")
    annotationProcessor("io.github.sequelcore:mortar-processor:0.1.0-alpha.1")
    testImplementation("io.github.sequelcore:mortar-testkit:0.1.0-alpha.1")
}
```

## Verify

```bash
./gradlew check
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

On Windows, use `gradlew.bat check` from the repository root.
