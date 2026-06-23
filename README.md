# Mortar

Mortar is Java-first, refactor-safe, SQL-transparent query authoring for
Spring and PostgreSQL.

Mortar keeps query construction in Java while making the SQL visible before it
executes. It is not an ORM: it does not provide managed entity state, lazy
loading, identity maps, implicit graph traversal, or repository method-name
query derivation.

Status: alpha. Java artifacts are prepared as `0.1.0-alpha.3`. Rust crates
and the VS Code extension are published as `0.1.0`. Mortar remains pre-`1.0`;
APIs may still change, and production adoption should pin exact versions and
review migration notes.

## Current Scope

- Java 25 and Spring Boot 4.1.x.
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
- [Current release plan](docs/plan.md)
- [Architecture decisions](docs/adr/index.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)

## Examples And Demo Path

The repository includes compile-backed examples for the current alpha:

- `examples/spring-boot-postgres`
- `examples/clean-architecture-postgres`
- `examples/query-corpus-*`

The official public backend demo is
[`sequelcore/sequel-backend-demo`](https://github.com/sequelcore/sequel-backend-demo).
It is a standalone Spring Boot backend that consumes Mortar from Maven Central
and starts with Mortar-only repository adapters. Other Sequel libraries should
be added there only after they are release-ready.

## Install

Java/Spring artifacts:

```kotlin
dependencies {
    implementation("io.github.sequelcore:mortar-core:0.1.0-alpha.3")
    implementation("io.github.sequelcore:mortar-dialect-postgres:0.1.0-alpha.3")
    implementation("io.github.sequelcore:mortar-runtime-jdbc:0.1.0-alpha.3")
    implementation("io.github.sequelcore:mortar-spring-boot-starter:0.1.0-alpha.3")
    annotationProcessor("io.github.sequelcore:mortar-processor:0.1.0-alpha.3")
    testImplementation("io.github.sequelcore:mortar-testkit:0.1.0-alpha.3")
}
```

Rust tooling crates:

```bash
cargo install sequel-mortar-cli --version 0.1.0
cargo install sequel-mortar-lsp --version 0.1.0
```

VS Code extension:

Install `sequelcore.mortar-vscode` from the VS Code Marketplace using the
pre-release channel.

The extension is a lightweight client. It does not bundle the Rust executables;
install `sequel-mortar-cli` and `sequel-mortar-lsp` separately and keep
`mortar` and `mortar-lsp` on `PATH`, or configure their paths explicitly.

## Quick Start

Configure the Spring Boot starter for PostgreSQL:

```properties
mortar.dialect=postgres
```

If the Rust tools are not on `PATH`, configure VS Code explicitly:

```json
{
  "mortar.lsp.path": "mortar-lsp",
  "mortar.cli.path": "mortar",
  "mortar.postgres.connection": "postgres://user:password@localhost:5432/app"
}
```

Model classes are annotated so the processor can generate `Q*` metamodels:

```java
@MortarEntity(table = "clients")
public record Client(
    @MortarId @MortarColumn("id") Long id,
    @MortarColumn("name") String name,
    @MortarColumn("active") boolean active
) {}
```

Repository adapters build inspectable query values and execute them explicitly.
Common primary-key reads use the generated fixed-read facade:

```java
MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT
    .read(renderer)
    .findById(clientId)
    .named("ClientRepository.findById");

Optional<QClient.FindByIdRow> row = jdbcClient.fetchOptional(query);
```

Explicit full-table reads are generated too, and should be reserved for small
reference data or bounded fixtures:

```java
List<QClient.FindAllRow> rows = jdbcClient.fetch(
    QClient.CLIENT
        .read(renderer)
        .findAll()
        .named("ClientRepository.findAll")
);
```

Application-specific reads use the Java DSL for predicates, projections,
sorting, and pagination:

```java
QuerySpec query = db.from(CLIENT)
    .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
    .where(client -> client.active.eq(true))
    .orderBy(client -> client.id.asc())
    .page(MortarPage.of(page, size))
    .named("ClientRepository.findActivePage")
    .build();
```

Scalar reads use DSL terminals:

```java
long activeCount = jdbcClient.fetchOne(
    db.from(CLIENT)
        .where(client -> client.active.eq(true))
        .count(renderer)
        .named("ClientRepository.countActive")
);

boolean exists = jdbcClient.fetchOne(
    db.from(CLIENT)
        .where(client -> client.id.eq(clientId))
        .exists(renderer)
        .named("ClientRepository.existsById")
);
```

Writes use explicit mutation specs. Non-returning mutations return row counts:

```java
MortarBoundMutation deactivate = MortarBoundMutation.unnamed(
        new UpdateSpec(
            CLIENT.table,
            List.of(Assignment.of(CLIENT.active, false)),
            List.of(CLIENT.id.eq(clientId)),
            List.of()
        ),
        renderer
    )
    .named("ClientRepository.deactivate");

int changedRows = jdbcClient.execute(deactivate);
```

PostgreSQL `RETURNING` uses a returning mutation value:

```java
MortarReturningMutation<ClientSummary> create = MortarReturningMutation.unnamed(
        new InsertSpec(
            CLIENT.table,
            List.of(
                Assignment.of(CLIENT.id, clientId),
                Assignment.of(CLIENT.name, name),
                Assignment.of(CLIENT.active, true)
            ),
            List.of(CLIENT.id, CLIENT.name)
        ),
        renderer,
        ClientSummary.class
    )
    .named("ClientRepository.create");

ClientSummary created = jdbcClient.fetchOptional(create).orElseThrow();
```

Adapter tests assert the generated SQL before runtime:

```java
assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(clientId)
    .hasParameterTypes(Long.class);
```

The current first-user path is:

1. Run `gradlew.bat :examples:spring-boot-postgres:test`.
2. Read [`docs/getting-started.md`](docs/getting-started.md).
3. Copy repository shapes from [`docs/query-recipes.md`](docs/query-recipes.md).
4. Use [`docs/usage-guide.md`](docs/usage-guide.md) for DSL reads, scalars,
   mutations, Spring Boot wiring, and Clean Architecture placement.

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
