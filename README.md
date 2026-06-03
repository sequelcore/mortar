# Mortar

Mortar is Java-first, refactor-safe, SQL-transparent query authoring for
Spring and PostgreSQL.

Mortar keeps query construction in Java while making the SQL visible before it
executes. It is not an ORM: it does not provide managed entity state, lazy
loading, identity maps, implicit graph traversal, or repository method-name
query derivation.

Status: pre-release. The first planned public version is `0.1.0-alpha.1`; it
has not been published to Maven Central or crates.io yet.

## Current Scope

- Java 21 and Spring Boot 3.5.x.
- PostgreSQL 16 through a dedicated PostgreSQL renderer.
- Generated Java metamodels for annotated models.
- Generated fixed reads: `findById` and explicit `findAll`.
- Java DSL reads with predicates, joins, projections, sorting, and pagination.
- Scalar `count` and `exists` through DSL scalar terminals.
- Insert, update, delete, row-count mutations, and PostgreSQL `RETURNING`.
- JDBC runtime execution through explicit `MortarJdbcClient` calls.
- Spring Boot starter auto-configuration for PostgreSQL/JDBC wiring.
- SQL assertions, snapshots, metadata, CLI, LSP, and VS Code SQL visibility.

Mortar currently does not claim broad dialect support, broad ORM replacement,
application migration compatibility, or performance superiority over JDBC,
PostgreSQL, jOOQ, QueryDSL, Hibernate, or Spring Data.

## Modules

```text
java/core                    Pure query model and DSL contracts
java/processor               Annotation processor and generated metamodels
java/dialect-postgres        PostgreSQL rendering
java/runtime-jdbc            JDBC execution adapter
java/spring-boot-starter     Spring Boot integration
java/testkit                 SQL assertions and snapshot helpers
editors/vscode               VS Code client for Mortar LSP
editors/intellij             IntelliJ plugin adapter
rust/crates/mortar-cli       CLI tooling
rust/crates/mortar-compiler  Metadata, diagnostics, and snapshot tooling
rust/crates/mortar-lsp       Language Server Protocol server
examples/spring-boot-postgres
examples/clean-architecture-postgres
```

## Documentation

- [Getting started](docs/getting-started.md)
- [Usage guide](docs/usage-guide.md)
- [Query recipes](docs/query-recipes.md)
- [API reference](docs/api-reference.md)
- [Spring Boot PostgreSQL example](docs/spring-boot-postgres-example.md)
- [DDD and Clean Architecture example](docs/ddd-clean-architecture-example.md)
- [Query safety and refactors](docs/refactor-safety.md)
- [SQL snapshots](docs/sql-snapshots.md)
- [Testkit](docs/testkit.md)
- [LSP](docs/lsp.md)
- [VS Code](docs/vscode.md)
- [Build metadata](docs/metadata.md)
- [Performance](docs/performance.md)
- [Benchmarks](docs/benchmarks/README.md)
- [Comparison](docs/comparison.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Release policy](docs/release.md)
- [Roadmap](docs/roadmap.md)
- [Current R24 readiness plan](docs/plan.md)
- [Architecture decisions](docs/adr/index.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)

## Install Preview

These coordinates are planned for the first alpha and are shown so examples can
stabilize before publication:

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

Until artifacts are published, use the repository examples and local builds.

## Verify

```bash
gradlew.bat check --no-daemon
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
cd ../editors/vscode
bun run typecheck
```
