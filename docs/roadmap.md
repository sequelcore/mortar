# Mortar Roadmap

Date: 2026-05-31
Status: Canonical

This document is the canonical roadmap for Mortar. It must be updated whenever a roadmap slice is completed, changed, split, deferred, or removed.

Mortar is a public Java + Rust project for Java-first, refactor-safe, SQL-transparent data access in Spring applications.

## Product Thesis

Java developers should be able to write queries as normal Java code, keep IDE refactor safety, and still see the exact SQL that will execute.

Mortar exists because the current Java persistence market forces a tradeoff:

- Spring Data JPA is productive, but string queries and complex repository behavior can become hard to trace.
- JPA Criteria is type-aware, but its ergonomics are poor for day-to-day application code.
- QueryDSL and Ebean query beans validate the generated-type approach, but leave room for stronger SQL transparency and Spring-first tooling.
- jOOQ validates type-safe SQL and code generation, but it is SQL-first rather than Java-domain-first.
- Hibernate can be useful, but Mortar must avoid opaque SQL generation, hidden lazy loading, and surprise N+1 behavior.
- SQLx and SQLDelight validate compile-time query tooling, offline analysis, and generated APIs, but they start from SQL text rather than Java code.

Mortar's position:

> Java-first query authoring with SQL transparency as a first-class product feature.

## Non-Negotiable Standards

Mortar follows Sequel engineering standards from day one:

- Java 21 minimum; Java 25 compatibility should be tracked once the core API stabilizes.
- Rust stable toolchain for CLI/compiler tooling.
- DDD and Clean Architecture boundaries.
- No framework dependency in `java/core`.
- No wildcard imports.
- No dead code, no speculative modules, no compatibility hacks without an ADR.
- Tests before implementation for behavior changes.
- JaCoCo coverage minimum: 80%.
- Rust gates: `cargo fmt`, `cargo clippy -D warnings`, `cargo test`.
- PostgreSQL behavior must be verified with Testcontainers before claiming database compatibility.
- Performance claims require JMH or reproducible benchmark evidence.
- Architecture decisions require ADRs.
- Public API changes require migration notes.

## Architectural Contract

Java owns the user-facing developer experience:

- fluent DSL;
- generated metamodels;
- annotation processor;
- JDBC runtime;
- Spring Boot integration;
- testkit assertions;
- records/DTO mapping.

Rust owns the toolchain:

- CLI;
- offline inspection;
- SQL snapshots;
- query diagnostics;
- `EXPLAIN` orchestration;
- future LSP server;
- benchmark/report helpers.

Rust must not be required in the hot path of normal Spring query execution unless a future ADR proves the benefit outweighs deployment complexity.

## Slice Status Model

Each slice uses one of these states:

- `Planned`: accepted direction, not started.
- `In Progress`: implementation exists but is not complete.
- `Blocked`: cannot continue without an external decision or dependency.
- `Done`: implemented, documented, tested, and verified.
- `Replaced`: superseded by another slice or ADR.

`Done` requires:

- code or documentation merged into the expected module;
- tests or verification evidence listed in this roadmap;
- relevant docs updated;
- ADR added when architecture changed;
- no known failing quality gate.

## Roadmap Slices

### R0: Project Foundation

Status: Done

Goal: Establish Mortar as a professional Java + Rust public project with enforceable standards.

Slices:

- R0.1: Gradle multi-module Java build. Status: Done.
- R0.2: Cargo workspace for Rust CLI/compiler. Status: Done.
- R0.3: Project docs: README, architecture spec, ADR index, research notes, roadmap. Status: Done.
- R0.4: CI for Java and Rust gates. Status: Done.
- R0.5: Coverage, wildcard import, compiler warning gates. Status: Done.
- R0.6: Contributor standards, commit rules, release rules, security policy. Status: Done.
- R0.7: License and package metadata. Status: Done.

Current evidence:

- `gradlew.bat clean check` passed on 2026-05-31.
- `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test` passed on 2026-05-31.
- Public governance docs exist: `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `CHANGELOG.md`, and `docs/release.md`.
- Licensing decision recorded in `docs/adr/0002-apache-2-license.md`; project license files are `LICENSE` and `NOTICE`.
- CI runs the same Java gate as local development: `./gradlew check`.
- Java publishing metadata declares group `io.github.sequelcore`, Apache-2.0 license, SCM, and developer metadata.
- Rust workspace/crates declare Apache-2.0 package metadata.

Exit criteria:

- CI mirrors local gates. Status: Done.
- `docs/roadmap.md` is linked from README and project guide. Status: Done.
- License, contribution guide, and security policy exist. Status: Done.

### R1: Core Query Model

Status: Done

Goal: Define a small, immutable, framework-free query AST that can represent transparent query intent.

Slices:

- R1.1: Table, column, predicate, sort, parameter, and query spec primitives. Status: Done.
- R1.2: Joins with explicit join type and relationship metadata. Status: Done.
- R1.3: Projection model for DTOs, records, scalars, and nested rows. Status: Done.
- R1.4: Boolean predicate composition: `and`, `or`, grouped predicates. Status: Done.
- R1.5: Null semantics: `isNull`, `isNotNull`, nullable-column metadata. Status: Done.
- R1.6: Comparison operators: `eq`, `ne`, `gt`, `gte`, `lt`, `lte`, `between`, `in`. Status: Done.
- R1.7: String operators with explicit collation/case strategy. Status: Done.
- R1.8: Query metadata model: touched tables, touched columns, joins, parameters. Status: Done.
- R1.9: Stable diagnostics model for compile/build/IDE errors. Status: Done.

Verification:

- Unit tests for every AST primitive and invalid state.
- Mutation-resistant tests for immutability.
- No dependency from `java/core` to Spring, JDBC, JPA, PostgreSQL, or IntelliJ.

Current evidence:

- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for explicit inner and left join query specs/rendering.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for boolean composition, null predicates, and comparison predicates.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for touched table, column, and join metadata extraction.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for explicit string case/collation strategy and stable diagnostic model.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for scalar, record, DTO, and nested projection modeling.

### R2: Java Metamodel Generation

Status: Done

Goal: Generate refactor-safe Java query types from application model classes.

Slices:

- R2.1: `@MortarEntity`, `@MortarColumn`, `@MortarId`, and relationship annotations. Status: Done.
- R2.2: Generate `Q*` or `*Table` classes from annotated Java types. Status: Done.
- R2.3: Support Java records as projection targets. Status: Done.
- R2.4: Support JPA annotations as optional input without making JPA a core dependency. Status: Done.
- R2.5: Incremental Gradle compilation support. Status: Done.
- R2.6: Compiler diagnostics for unsupported field types, duplicate columns, missing IDs, and ambiguous relationships. Status: Done.
- R2.7: Generated-source documentation that can expose SQL metadata to IDEs. Status: Done.
- R2.8: Generate canonical `findById` JDBC executor for annotated entities. Status: Done.

Verification:

- Compile-testing suite for annotation processor behavior.
- Golden-file tests for generated Java.
- Refactor-safety test: renamed model field breaks old query code at compile time.

Current evidence:

- `gradlew.bat :java:processor:test` passed on 2026-05-31 for entity, column, id, and relationship annotation contracts.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for `javac` annotation processing that generates `Q*` metamodel classes from annotated Java fields.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for `javac` annotation processing that generates `Q*` metamodel classes from annotated Java record components.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for stable compiler diagnostics covering missing ids, duplicate columns, unsupported generic column types, and ambiguous relationships.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for optional `jakarta.persistence` entity/table/column/id input using annotation mirrors and no direct Jakarta dependency.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for Gradle incremental annotation processor metadata declaring `MortarProcessor` as `isolating`.
- `gradlew.bat :java:processor:test` passed on 2026-05-31 for generated-source Javadocs exposing SQL table and column metadata.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest --no-daemon`, `gradlew.bat :java:processor:test --no-daemon`, and `gradlew.bat check --no-daemon` passed on 2026-06-01 for generated `findById` executors on `Q*` metamodels. The generated executor exposes parameter and row records, pre-renders SQL through a caller-provided `QueryRenderer`, implements `MortarGeneratedQuery<P, T>`, binds primary-key parameters with direct JDBC setters, maps selected columns by projection index, and compiles with normal `MortarJdbcClient.fetchOptional(...)` usage. Changed modules/docs: `java/processor`, `build.gradle.kts`, `docs/api-reference.md`, `docs/spec/architecture.md`, `docs/research/postgres-performance-strategy.md`, `docs/getting-started.md`, `README.md`. Architecture note: the processor still does not render SQL; generated application source targets the runtime JDBC contract recorded in ADR-0004. Coverage note: generated `Q*` classes are excluded from JaCoCo coverage verification so user-written modules do not fail coverage because of generated source expansion; publishable/runtime modules keep the 80% gate.

### R3: Java-First DSL

Status: Done

Goal: Provide the public API users actually write in Spring applications.

Slices:

- R3.1: `db.from(ClientTable.CLIENT)` fluent entry point. Status: Done.
- R3.2: Lambda-based ergonomic API where generated table types are inferred. Status: Done.
- R3.3: Typed predicates that reject invalid value types at compile time. Status: Done.
- R3.4: Typed sorting and pagination. Status: Done.
- R3.5: Joins that require valid relationship paths. Status: Done.
- R3.6: Projection API for records and DTO constructors. Status: Done.
- R3.7: Query object naming convention for repository/debug output. Status: Done.
- R3.8: Escape hatch for raw SQL fragments behind explicit unsafe API naming. Status: Done.

Verification:

- API readability examples in `examples/`.
- Compile-fail tests for invalid field/value usage.
- No string-based field references in public DSL.

Current evidence:

- `gradlew.bat :java:core:test :java:processor:test` passed on 2026-05-31 for `MortarTable` DSL entry and generated `Q*` classes implementing that contract.
- `gradlew.bat :java:core:test` passed on 2026-05-31 for lambda-based `where`, `select`, `orderBy`, and `page` APIs over inferred generated table types.
- `gradlew.bat :java:core:test` passed on 2026-05-31 for `javac` compile-fail coverage proving invalid predicate value types are rejected at compile time.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for record/DTO projection API, named queries, and explicit `unsafeWhereRaw` escape hatch rendering.
- `gradlew.bat :java:core:test :java:processor:test` passed on 2026-05-31 for generated relationship paths and lambda joins through `RelationRef`.

### R4: PostgreSQL Dialect

Status: Done

Goal: Make PostgreSQL the first-class database with transparent, stable SQL output.

Slices:

- R4.1: Identifier rendering and validation. Status: Done.
- R4.2: Select, where, order by, limit, offset. Status: Done.
- R4.3: Inner and left joins. Status: Done.
- R4.4: Insert, update, delete. Status: Done.
- R4.5: Returning clauses. Status: Done.
- R4.6: PostgreSQL-specific operators: `ilike`, arrays, JSONB, full-text search. Status: Done.
- R4.7: SQL formatting mode for human-readable logs/snapshots. Status: Done.
- R4.8: Rendered-query metadata: SQL, parameters, tables, columns, joins. Status: Done.

Verification:

- Snapshot tests for generated SQL.
- PostgreSQL Testcontainers integration tests.
- SQL syntax validation against PostgreSQL.

Current evidence:

- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for PostgreSQL inner and left join rendering.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for PostgreSQL rendering of boolean composition, null predicates, comparisons, `between`, and `in`.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for rendered-query metadata attachment.
- `gradlew.bat :java:core:test :java:dialect-postgres:test` passed on 2026-05-31 for PostgreSQL string `like`/`ilike` rendering with optional collation.
- `gradlew.bat :java:core:test --tests dev.mortar.core.MutationSpecTest :java:dialect-postgres:test --tests dev.mortar.postgres.PostgresMutationRendererTest --tests dev.mortar.postgres.PostgresMutationIntegrationTest` passed on 2026-05-31 for core mutation specs, mutation table-ownership validation, insert/update/delete rendering, bare-column mutation predicates, returning clauses, mutation metadata, and execution against PostgreSQL 16 through Testcontainers. Changed modules: `java/core`, `java/dialect-postgres`. Migration note: this adds new public mutation spec and renderer overload APIs; existing select query APIs remain compatible. Architecture note: no new ADR because the change stays inside ADR-0001 dialect/core boundaries. Dependency note: PostgreSQL integration tests use the current Testcontainers 2.0.5 PostgreSQL/JUnit Jupiter modules and pgJDBC 42.7.11.
- `gradlew.bat :java:core:test --tests dev.mortar.core.DialectPredicateTest :java:dialect-postgres:test --tests dev.mortar.postgres.PostgresSpecificPredicateTest --tests dev.mortar.postgres.PostgresSpecificPredicateIntegrationTest` passed on 2026-05-31 for dialect-extension predicates, PostgreSQL array `@>`/`&&`, JSONB `@>`, full-text `@@` with `websearch_to_tsquery`, metadata, fail-fast input validation, and execution against PostgreSQL 16 through Testcontainers. Changed modules: `java/core`, `java/dialect-postgres`. Migration note: this adds the public `PostgresPredicates` factory surface and a generic core `DialectPredicate` extension point; existing query APIs remain compatible. Architecture note: no new ADR because PostgreSQL-specific APIs and rendering stay in `java/dialect-postgres`, while core only models dialect-neutral extension predicates.
- `gradlew.bat :java:dialect-postgres:test --tests dev.mortar.postgres.PostgresSqlFormattingTest` passed on 2026-05-31 for compact default SQL and pretty select/mutation formatting for logs and snapshots. Changed module: `java/dialect-postgres`. Migration note: this adds `PostgresSqlFormat` and `PostgresQueryRenderer(PostgresSqlFormat)`; existing `new PostgresQueryRenderer()` behavior remains compact and compatible.

### R5: JDBC Runtime

Status: Done

Goal: Execute rendered queries predictably through JDBC with minimal overhead.

Slices:

- R5.1: Prepared statement execution. Status: Done.
- R5.2: Parameter binding by Java type. Status: Done.
- R5.3: Row mapping to scalar values. Status: Done.
- R5.4: Row mapping to records and DTO constructors. Status: Done.
- R5.5: Batch execution. Status: Done.
- R5.6: Transaction participation without owning transaction policy. Status: Done.
- R5.7: Exception translation with SQL and query metadata attached. Status: Done.
- R5.8: Query logging hook with redaction support. Status: Done.

Verification:

- JDBC unit tests with controlled doubles.
- PostgreSQL integration tests with Testcontainers.
- Allocation and throughput benchmarks against plain JDBC.

Current evidence:

- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --tests dev.mortar.jdbc.MortarJdbcClientIntegrationTest` passed on 2026-05-31 for prepared statement execution, Java-type parameter binding including null handling, scalar row mapping, SQL exception wrapping, and execution against PostgreSQL 16 through Testcontainers. Changed module: `java/runtime-jdbc`. Architecture note: runtime executes rendered plans through JDBC and does not own query semantics; PostgreSQL renderer usage is test-scoped only.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --tests dev.mortar.jdbc.MortarJdbcClientIntegrationTest` passed on 2026-05-31 for projection-aware row mapping to Java records and DTO constructors, including PostgreSQL 16 Testcontainers coverage. Changed module: `java/runtime-jdbc`. Migration note: this adds `MortarJdbcClient.fetch(QuerySpec, Class<T>)`; existing `fetch(QuerySpec, RowMapper<T>)` remains compatible. Architecture note: runtime consumes core `Projection` metadata and does not add query semantics.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --tests dev.mortar.jdbc.MortarJdbcClientIntegrationTest` passed on 2026-05-31 for JDBC mutation batch execution with homogeneous rendered SQL validation, `PreparedStatement.addBatch`, type-aware parameter binding, and PostgreSQL 16 Testcontainers insert-batch coverage. Changed module: `java/runtime-jdbc`. Migration note: this adds `MortarJdbcClient.executeBatch(List<? extends MutationSpec>)`; existing fetch APIs remain compatible.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --tests dev.mortar.jdbc.MortarJdbcClientIntegrationTest` passed on 2026-05-31 for caller-owned JDBC connection participation, proving Mortar does not close, commit, or roll back external connections and can participate in a PostgreSQL 16 transaction controlled by the caller. Changed module: `java/runtime-jdbc`. Migration note: this adds `MortarJdbcClient(Connection, QueryRenderer)`; DataSource-backed construction remains compatible.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest` passed on 2026-05-31 for exception translation that attaches rendered SQL, parameters, and query metadata to query and batch failures. Changed module: `java/runtime-jdbc`. Migration note: `MortarJdbcException` now exposes `sql()`, `parameters()`, and `metadata()` while preserving existing unchecked exception behavior.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest` passed on 2026-05-31 for query and batch logging hooks with redacted parameter values and metadata propagation. Changed module: `java/runtime-jdbc`. Migration note: this adds optional `MortarJdbcLogger` constructors plus immutable `MortarJdbcLogEvent`, `MortarJdbcOperation`, and `MortarJdbcParameter`; existing constructors use a no-op logger.

### R6: Spring Boot Integration

Status: Done

Goal: Make Mortar feel native in Spring Boot without coupling core to Spring.

Slices:

- R6.1: Auto-configuration for `MortarDb`, renderer, and JDBC client. Status: Done.
- R6.2: Properties: dialect, logging, SQL formatting, diagnostics. Status: Done.
- R6.3: Spring transaction integration. Status: Done.
- R6.4: Repository examples for Clean Architecture infrastructure adapters. Status: Done.
- R6.5: Spring Boot Actuator diagnostics endpoint. Status: Done.
- R6.6: Starter compatibility matrix for Spring Boot 3.5 and 4.x. Status: Done.

Verification:

- `ApplicationContextRunner` tests.
- Spring Boot sample application.
- Transaction tests against PostgreSQL Testcontainers.

Current evidence:

- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarAutoConfigurationTest` passed on 2026-05-31 for Spring Boot auto-configuration registration, user bean backoff, default PostgreSQL renderer, and `DataSource`-conditional `MortarJdbcClient` creation. Changed module: `java/spring-boot-starter`. Architecture note: Spring Boot remains an adapter; core, dialect, and runtime modules do not depend on Spring.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarAutoConfigurationTest` passed on 2026-05-31 for `mortar.sql-format`, `mortar.jdbc.logging.enabled`, diagnostics property binding, user-provided logger backoff, and logger injection into the auto-configured JDBC client. Changed module: `java/spring-boot-starter`. Migration note: this adds `MortarSpringProperties`; default SQL format remains compact and JDBC logging remains disabled unless configured.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarAutoConfigurationTest` passed on 2026-05-31 for Spring-managed JDBC transaction participation through `TransactionAwareDataSourceProxy`, proving Mortar uses the transaction-bound connection instead of opening an independent one. Changed module: `java/spring-boot-starter`. Architecture note: Spring transaction semantics remain in the starter adapter; runtime JDBC stays Spring-free.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarCleanArchitectureRepositoryExampleTest` passed on 2026-05-31 for a compiled Clean Architecture repository-port example wired through Spring Boot auto-configuration. Changed modules/docs: `java/spring-boot-starter`, `docs/examples/spring-clean-architecture-repository.md`. Architecture note: domain owns the repository port, infrastructure owns `MortarJdbcClient`, and Spring only wires the adapter.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarDiagnosticsEndpointTest` passed on 2026-05-31 for a conditional Spring Boot Actuator `mortar` diagnostics endpoint and `mortar.diagnostics.enabled=false` disablement. Changed module: `java/spring-boot-starter`. Migration note: this adds `MortarDiagnosticsEndpoint` when Actuator is on the classpath; the endpoint reports non-secret starter state only.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarSpringBootCompatibilityTest` passed on 2026-05-31 for the documented Spring Boot 3.5 baseline. Changed docs/tests: `docs/spring-boot-compatibility.md`, `java/spring-boot-starter`. Compatibility note: Spring Boot 3.5.x is tested; Spring Boot 4.x remains planned until a dedicated CI lane compiles and runs the starter tests against 4.x.

### R7: Testkit And Snapshots

Status: Done

Goal: Make SQL transparency testable and normal.

Slices:

- R7.1: AssertJ assertions for rendered SQL. Status: Done.
- R7.2: Parameter assertions. Status: Done.
- R7.3: Snapshot file format. Status: Done.
- R7.4: Snapshot update command. Status: Done.
- R7.5: Query metadata assertions. Status: Done.
- R7.6: Explain-plan assertions for integration tests. Status: Done.
- R7.7: Regression fixtures for SQL rendering edge cases. Status: Done.

Verification:

- Self-hosted tests using Mortar-generated SQL snapshots.
- Stable failure messages designed for CI logs.

Current evidence:

- `gradlew.bat :java:testkit:test --tests dev.mortar.testkit.MortarSqlAssertionsTest` passed on 2026-05-31 for AssertJ rendered SQL assertions through `hasSql(...)`/`renders(...)`, parameter value assertions, parameter Java type assertions, and stable CI-focused failure messages. Changed module/docs: `java/testkit`, `docs/testkit.md`, `README.md`, `docs/plan.md`. Migration note: this adds `hasSql(...)`, `hasParameters(...)`, and `hasParameterTypes(...)`; the existing `renders(...)` assertion remains as a compatible alias.
- `cargo test -p mortar-compiler` passed on 2026-05-31 for the canonical `mortar-sql-snapshot-v1` JSON file format, parse/render support, sorted stable output, invalid-format validation, blank snapshot validation, and duplicate-name validation. Changed module/docs: `rust/crates/mortar-compiler`, `docs/sql-snapshots.md`, `docs/plan.md`. Migration note: this adds public Rust snapshot data types plus `parse_sql_snapshot_file(...)`, `render_sql_snapshot_file(...)`, `empty_sql_snapshot_file(...)`, and `sql_snapshot_format(...)`.
- `cargo test` passed on 2026-05-31 for the Rust CLI snapshot update path that creates a new canonical snapshot file or replaces an existing entry by snapshot name. Changed module/docs: `rust/crates/mortar-cli`, `docs/sql-snapshots.md`, `README.md`. Migration note: this adds `mortar snapshot update --file <path> --name <query-name> --sql <sql>` and the public helper `update_sql_snapshot_content(...)`.
- `gradlew.bat :java:testkit:test --tests dev.mortar.testkit.MortarSqlAssertionsTest` passed on 2026-05-31 for query metadata assertions over tables, columns, and joins with stable SQL-inclusive failure messages. Changed module/docs: `java/testkit`, `docs/testkit.md`, `docs/plan.md`. Migration note: this adds `hasTables(...)`, `hasColumns(...)`, and `hasJoins(...)` to the public testkit assertion API.
- `gradlew.bat :java:testkit:test --tests dev.mortar.testkit.MortarExplainPlanAssertionsTest` passed on 2026-05-31 for string-based integration-test assertions over `EXPLAIN` output, including expected plan nodes, expected index usage, and sequential-scan rejection with stable CI-focused failure messages. Changed module/docs: `java/testkit`, `docs/testkit.md`, `docs/plan.md`. Migration note: this adds `MortarExplainPlanAssertions.assertThatExplainPlan(...)`; it intentionally does not add JDBC, Spring, or PostgreSQL driver dependencies to testkit.
- `gradlew.bat :java:dialect-postgres:test --tests dev.mortar.postgres.PostgresRenderingFixtureTest` passed on 2026-05-31 for SQL rendering regression fixtures covering nested boolean grouping, explicit unsafe raw parameter order, and pretty SQL join pagination stability through the public Mortar testkit assertions. Changed module/docs: `java/dialect-postgres`, `java/testkit`, `docs/testkit.md`, `docs/plan.md`. Dependency note: `java/dialect-postgres` now uses `java/testkit` as a test-only dependency to self-host SQL transparency assertions.

### R8: Rust CLI And Compiler Tooling

Status: Done

Goal: Provide fast developer tooling around generated SQL and query metadata.

Slices:

- R8.1: `mortar doctor`. Status: Done.
- R8.2: `mortar inspect --sql`. Status: Done.
- R8.3: Read Mortar metadata files emitted by Java build. Status: Done.
- R8.4: `mortar snapshot check/update`. Status: Done.
- R8.5: `mortar explain` against PostgreSQL. Status: Done.
- R8.6: `mortar report` for query inventory. Status: Done.
- R8.7: CI-friendly JSON output. Status: Done.
- R8.8: Redaction rules for parameters and connection strings. Status: Done.

Verification:

- Rust unit tests.
- CLI snapshot tests.
- Cross-platform Windows/Linux CI.

Current evidence:

- `cargo test -p mortar-cli` passed on 2026-05-31 for `mortar doctor` report generation and `mortar inspect --sql` text output normalization. Changed module/docs: `rust/crates/mortar-cli`, `docs/cli.md`, `README.md`, `docs/plan.md`. Migration note: `mortar doctor` now reports the active snapshot format in addition to readiness; `mortar inspect --sql` behavior remains compatible and is routed through tested CLI library helpers.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorMetadataFileTest` passed on 2026-05-31 for annotation-processor emission of `META-INF/mortar/entities.json` using `mortar-metadata-v1`. `cargo test -p mortar-compiler parses_mortar_metadata_file` passed on 2026-05-31 for Rust parsing of that metadata format. `cargo test -p mortar-cli` passed on 2026-05-31 for `mortar inspect --metadata-file` text inventory output. Changed modules/docs: `java/processor`, `rust/crates/mortar-compiler`, `rust/crates/mortar-cli`, `docs/metadata.md`, `docs/cli.md`, `README.md`, `docs/plan.md`. Architecture note: metadata is build/tooling input only; it does not add Rust or CLI to the Java runtime path.
- `cargo test -p mortar-cli` passed on 2026-05-31 for `mortar snapshot check` canonical validation and `mortar snapshot update` canonical create/replace behavior. Changed module/docs: `rust/crates/mortar-cli`, `docs/cli.md`, `docs/sql-snapshots.md`. Migration note: snapshot update was introduced during R7 and is now paired with check behavior for the R8 CLI workflow.
- `cargo test -p mortar-compiler builds_postgres_explain_sql` passed on 2026-05-31 for PostgreSQL `EXPLAIN (format text)` SQL construction. `cargo test -p mortar-cli explains_sql_against_postgres -- --nocapture` passed on 2026-05-31 against a real PostgreSQL container through Testcontainers Rust. Changed modules/docs: `rust/crates/mortar-compiler`, `rust/crates/mortar-cli`, `docs/cli.md`. Dependency note: `rust/crates/mortar-cli` now uses the synchronous Rust `postgres` client for CLI-only diagnostics and Testcontainers Rust 0.13.0 as a dev-only integration-test dependency because newer Testcontainers releases require a newer Rust toolchain than Mortar's current Rust 1.85 baseline.
- `cargo test -p mortar-cli builds_metadata_report_for_query_inventory` passed on 2026-05-31 for `mortar report --metadata-file` inventory output from `mortar-metadata-v1` files. Changed module/docs: `rust/crates/mortar-cli`, `docs/cli.md`. Migration note: this adds a read-only report command over generated metadata; it does not inspect application classes directly.
- `cargo test -p mortar-cli json` passed on 2026-05-31 for CI-friendly JSON output helpers covering `doctor --json`, `inspect --sql --json`, `inspect --metadata-file --json`, and `report --json`; `snapshot check --json` uses the same JSON rendering path in the CLI command. Changed module/docs: `rust/crates/mortar-cli`, `docs/cli.md`. Migration note: text output remains the default for human use.
- `cargo test -p mortar-compiler redact` passed on 2026-05-31 for redaction of sensitive parameter values and PostgreSQL connection-string credentials/query secrets. Changed modules/docs: `rust/crates/mortar-compiler`, `rust/crates/mortar-cli`, `docs/cli.md`, `docs/sql-snapshots.md`. Migration note: `mortar explain` now wraps connection failures with a redacted connection string in diagnostic output.

### R9: Static Diagnostics And Safety Rules

Status: Done

Goal: Catch fragile, slow, or ambiguous query behavior before production.

Slices:

- R9.1: Missing pagination warning for collection queries. Status: Done.
- R9.2: Unsafe `select *` warning outside explicitly allowed cases. Status: Done.
- R9.3: Query without stable ordering warning for paginated results. Status: Done.
- R9.4: Unbounded `in` list warning. Status: Done.
- R9.5: Nullable relationship join warning. Status: Done.
- R9.6: Potential N+1 warning for repeated query pattern. Status: Done.
- R9.7: Index advisory based on filters, joins, and order clauses. Status: Done.
- R9.8: Schema drift detection between generated metadata and database. Status: Done.

Verification:

- Diagnostic golden tests.
- False-positive budget documented for every rule.
- Suppression mechanism with mandatory reason text.

Current evidence:

- `gradlew.bat :java:core:test --tests dev.mortar.core.QueryDiagnosticsTest` passed on 2026-05-31 for static query diagnostics covering missing pagination, default select-all projection, paginated queries without stable ordering, large `in` predicates over 100 values, and a no-warning control case. Changed module/docs: `java/core`, `docs/diagnostics.md`, `README.md`. Migration note: this adds `QueryDiagnostics.analyze(QuerySpec)` plus stable diagnostic codes `MORTAR_CORE_005`, `MORTAR_CORE_006`, and `MORTAR_CORE_007`; existing diagnostic codes remain compatible.
- `gradlew.bat :java:core:test --tests dev.mortar.core.QueryDiagnosticsTest` passed on 2026-05-31 for nullable relationship inner-join diagnostics. `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesRelationRefsForAnnotatedRelations` passed on 2026-05-31 for generated relation refs carrying nullable metadata. Changed modules/docs: `java/core`, `java/processor`, `rust/crates/mortar-compiler`, `docs/diagnostics.md`, `docs/metadata.md`. Migration note: `Join` and `RelationRef` now expose nullable relationship metadata while preserving existing constructors with non-nullable defaults; `@MortarRelation` adds `nullable()` with default `true`.
- `gradlew.bat :java:core:test --tests dev.mortar.core.QueryDiagnosticsTest` passed on 2026-05-31 for repeated rendered SQL pattern diagnostics over `RenderedQuery` batches, warning after the same SQL appears more than 10 times. Changed module/docs: `java/core`, `docs/diagnostics.md`. Migration note: this adds `QueryDiagnostics.analyzeRenderedQueries(List<RenderedQuery>)` and stable code `MORTAR_CORE_009`.
- `gradlew.bat :java:core:test --tests dev.mortar.core.QueryDiagnosticsTest` passed on 2026-05-31 for informational index advisories derived from filter, join, and order-by columns. Changed module/docs: `java/core`, `docs/diagnostics.md`. Migration note: this adds `MortarDiagnostic.info(...)` and stable code `MORTAR_CORE_010`; advisories are `INFO`, not warnings or errors.
- `cargo test -p mortar-compiler detects_schema_drift` passed on 2026-05-31 for pure generated-metadata versus database-schema drift detection. `cargo test -p mortar-cli checks_schema_against_postgres -- --nocapture` passed on 2026-05-31 against a real PostgreSQL container using `information_schema.columns`. Changed modules/docs: `rust/crates/mortar-compiler`, `rust/crates/mortar-cli`, `docs/cli.md`, `docs/diagnostics.md`. Migration note: this adds `mortar schema check --connection <postgres-url> --metadata-file <entities.json>` for CLI schema drift diagnostics.

### R10: Performance Program

Status: Done

Goal: Make performance measurable instead of marketing.

Slices:

- R10.1: JMH benchmark module. Status: Done.
- R10.2: Benchmark plain JDBC baseline. Status: Done.
- R10.3: Benchmark Mortar rendering overhead. Status: Done.
- R10.4: Benchmark Mortar JDBC execution overhead. Status: Done.
- R10.5: Benchmark jOOQ and QueryDSL reference rendering scenarios. Status: Done.
- R10.6: Allocation profiling. Status: Done.
- R10.7: Public benchmark report template. Status: Done.
- R10.8: Performance regression threshold in CI for stable benchmarks. Status: Done.
- R10.9: Real PostgreSQL execution benchmark against plain JDBC, Mortar, jOOQ, and QueryDSL. Status: Done.
- R10.10: Generated JDBC executor runtime contract and reusable prepared generated benchmark path. Status: Done.
- R10.11: Processor-generated `findById` executor benchmark harness. Status: Done.
- R10.12: Processor-generated `findById` latency profile. Status: Done.
- R10.13: Retained CI artifacts for repeated benchmark review. Status: Done.
- R10.14: Repeated full PostgreSQL throughput, allocation, and latency review. Status: Done.
- R10.15: Public-readiness PostgreSQL performance report draft. Status: Done.

Verification:

- Benchmarks use JMH and publish environment details.
- Claims include raw data, hardware/JDK version, warmup, forks, and confidence notes.

Current evidence:

- `gradlew.bat :java:benchmarks:compileJava` passed on 2026-05-31 for the JMH benchmark module and PostgreSQL rendering-overhead benchmark. Changed modules/docs: `java/benchmarks`, `settings.gradle.kts`, `docs/performance-report-template.md`, `README.md`. Dependency note: JMH 1.37 is used because Maven Central lists `org.openjdk.jmh:jmh-core` 1.37 as the current stable release. Migration note: benchmarks are isolated in `java/benchmarks` and do not affect runtime artifacts.
- `gradlew.bat :java:benchmarks:jmh -PjmhIncludes=JdbcExecutionBenchmark -PjmhArgs="-wi 0 -i 1 -f 1 -r 100ms -w 100ms"` passed on 2026-05-31 for plain JDBC baseline and Mortar JDBC execution benchmark harness smoke coverage. Changed module/docs: `java/benchmarks`, `docs/benchmarks/README.md`, `docs/performance-report-template.md`. Evidence note: this was a short harness validation run, not publishable performance data.
- `gradlew.bat :java:benchmarks:jmh -PjmhIncludes=ReferenceRenderingBenchmark -PjmhArgs="-wi 0 -i 1 -f 1 -r 100ms -w 100ms"` passed on 2026-05-31 for jOOQ and QueryDSL SQL rendering reference benchmark harness smoke coverage. Changed module/docs: `java/benchmarks`, `docs/benchmarks/README.md`, `docs/performance-report-template.md`. Dependency note: jOOQ 3.20.9 and QueryDSL SQL 5.1.0 are benchmark-scope dependencies; JAXB and JetBrains annotations are included because their published artifacts expose those annotations and Mortar compiles with `-Werror`. Scope note: Hibernate ORM execution benchmarks are not part of this rendering-reference slice because they require a session-factory/database fixture and would measure a different execution layer.
- `gradlew.bat :java:benchmarks:jmhAllocation -PjmhIncludes=PostgresRenderingBenchmark -PjmhArgs="-wi 0 -i 1 -f 1 -r 100ms -w 100ms"` passed on 2026-05-31 for JMH GC allocation profiler wiring. Changed module/docs: `java/benchmarks`, `docs/benchmarks/README.md`, `docs/performance-report-template.md`.
- `gradlew.bat :java:benchmarks:verifyBenchmarkThresholds` passed on 2026-05-31 for the bootstrap benchmark threshold contract in `docs/benchmarks/thresholds.json`; `gradlew.bat check` runs the same validation through the `java:benchmarks` check task. Changed module/docs: `java/benchmarks`, `docs/benchmarks/thresholds.json`, `docs/benchmarks/README.md`. Threshold note: bootstrap values are deliberately loose until stable baseline reports exist.
- Benchmark readiness follow-up on 2026-06-01 added reproducible `jmhBaseline` and `jmhBaselineAllocation` Gradle tasks, corrected the JDBC benchmark so plain JDBC and Mortar materialize the same `ClientRow` result shape, and removed noop logging event materialization from the default Mortar JDBC hot path. `gradlew.bat :java:runtime-jdbc:test --no-daemon`, `gradlew.bat :java:benchmarks:compileJava --no-daemon`, `gradlew.bat :java:benchmarks:jmhBaseline -PjmhIncludes=PostgresRenderingBenchmark --no-daemon`, `gradlew.bat :java:benchmarks:jmhBaseline -PjmhIncludes=ReferenceRenderingBenchmark --no-daemon`, `gradlew.bat :java:benchmarks:jmhBaseline -PjmhIncludes=JdbcExecutionBenchmark --no-daemon`, and `gradlew.bat :java:benchmarks:jmhBaselineAllocation -PjmhIncludes=JdbcExecutionBenchmark --no-daemon` passed. Internal results and limitations are documented in `docs/benchmarks/baseline-2026-06-01.md`.
- Real PostgreSQL benchmark follow-up on 2026-06-01 added `PostgresExecutionBenchmark`, `jmhPostgresExecution`, `jmhPostgresExecutionLatency`, and `jmhPostgresExecutionAllocation`. The benchmark uses PostgreSQL 16 through Testcontainers, a deterministic 1,000-row dataset, one live JDBC connection per JMH trial, and equal `ClientRow` materialization across plain JDBC, Mortar, jOOQ, and QueryDSL. `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest.executesPreRenderedQueryWithoutCallingRenderer --no-daemon`, `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`, `gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon`, and `gradlew.bat check --no-daemon` passed on 2026-06-01. Internal throughput results and limitations are documented in `docs/benchmarks/postgres-execution-2026-06-01.md`. Build note: `java:benchmarks` is excluded from JaCoCo coverage verification because JMH benchmark classes are executable measurement harnesses; runtime and publishable Java modules keep the 80% coverage gate.
- Single-row performance follow-up on 2026-06-01 added `MortarJdbcClient.fetchOptional(...)` and optional PostgreSQL benchmark scenarios for plain JDBC, Mortar, jOOQ, and QueryDSL. `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest.fetchOptionalMapsSingleRenderedRowWithoutCallingRenderer --tests dev.mortar.jdbc.MortarJdbcClientTest.fetchOptionalReturnsEmptyWhenNoRowsExist --tests dev.mortar.jdbc.MortarJdbcClientTest.fetchOptionalRejectsMultipleRows --no-daemon`, `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`, and `gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon` passed on 2026-06-01. Result note: best Mortar path (`mortarPreRenderedJdbcFetchOptional`) is close to best plain JDBC path but does not beat it in the full baseline.
- Performance research on 2026-06-01 concluded that Mortar's next credible path to beating ordinary/tuned JDBC is a generated query executor: pre-rendered SQL, direct generated binders, direct generated mappers, and an expanded benchmark matrix against ordinary JDBC, tuned PgJDBC, and maximum reusable-statement JDBC. Research and source links are documented in `docs/research/postgres-performance-strategy.md`.
- Generated executor follow-up on 2026-06-01 added `MortarGeneratedQuery<P, T>`, `MortarPreparedQuery<P, T>`, direct generated execution methods on `MortarJdbcClient`, reusable plain JDBC benchmark baselines, and generated-style PostgreSQL benchmark scenarios with projection-index row mapping. `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest --no-daemon`, `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`, and `gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon` passed on 2026-06-01. Result note: the full throughput baseline shows `mortarGeneratedJdbcFetch` as the strongest measured list path for this indexed lookup and generated paths beating ordinary plain JDBC in this run, while optional/reusable comparisons still need repeated latency and allocation runs before public claims. Architecture note: ADR-0004 keeps the generated executor API in `java/runtime-jdbc` because it exposes JDBC types.
- Processor-generated `findById` benchmark harness follow-up on 2026-06-01 added a benchmark-only `@MortarEntity`, enabled the Mortar annotation processor in `java:benchmarks`, and added plain JDBC, reusable plain JDBC, generated Mortar, and prepared generated Mortar scenarios for the SQL emitted by `QBenchmarkClient.BENCHMARK_CLIENT.findById(renderer)`. `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`, `gradlew.bat :java:benchmarks:check --no-daemon`, `gradlew.bat :java:benchmarks:jmhPostgresExecution "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon`, and `gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon` passed. Internal focused results show the prepared processor-generated list path at 1,682.463 ops/s ± 28.046, the prepared optional path at 1,659.540 ops/s ± 36.046, plain JDBC list at 1,638.574 ops/s ± 30.846, and reusable plain JDBC list at 1,601.128 ops/s ± 37.167. Allocation results show prepared processor-generated list at 837.660 B/op ± 35.691 versus reusable plain JDBC list at 839.167 B/op ± 38.658. Result note: this is internal benchmark evidence; repeated-run evidence is still required before public claims. Changed modules/docs: `java/benchmarks`, `docs/benchmarks/README.md`, `docs/benchmarks/postgres-execution-2026-06-01.md`, `docs/performance-report-template.md`, `docs/plan.md`.
- Processor-generated `findById` latency follow-up on 2026-06-01 ran `gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency "-PjmhIncludes=PostgresExecutionBenchmark.*FindById.*" --no-daemon` against PostgreSQL 16 through Testcontainers. Internal focused results show the prepared processor-generated list path with mean 0.601 ms/op ± 0.001, p50 0.588 ms/op, p95 0.688 ms/op, and p99 0.774 ms/op; plain JDBC list measured mean 0.626 ms/op ± 0.001, p50 0.612 ms/op, p95 0.724 ms/op, and p99 0.834 ms/op. Result note: this adds latency evidence for the focused `findById` group only; public claims still require repeated runs, release commit metadata, raw artifact retention, and reviewer sign-off. Changed docs: `docs/benchmarks/postgres-execution-2026-06-01.md`, `docs/roadmap.md`, `docs/plan.md`.
- Benchmark artifact retention follow-up on 2026-06-01 added a manual GitHub Actions `Benchmarks` workflow with `jmhIncludes`, `profile`, and `repeatCount` inputs. The workflow runs selected PostgreSQL JMH profiles with Docker available on `ubuntu-latest`, copies repeated run JSON files under `java/benchmarks/build/reports/jmh`, and uploads them with 30 day retention through `actions/upload-artifact`. `gradlew.bat verifyBenchmarkWorkflow --no-daemon` first failed while the workflow was absent and passed after `.github/workflows/benchmarks.yml` was added; `gradlew.bat check --no-daemon` runs the same workflow contract. Result note: this closes raw artifact retention wiring for benchmark review, but public claims still require actually running repeated workflows and reviewer sign-off. Changed files/docs: `.github/workflows/benchmarks.yml`, `build.gradle.kts`, `docs/benchmarks/README.md`, `docs/plan.md`, `docs/roadmap.md`.
- Repeated full-profile PostgreSQL benchmark follow-up on 2026-06-01 ran `gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon`, `gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation --no-daemon`, and `gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency --no-daemon` against PostgreSQL 16 through Testcontainers. Local JSON artifacts were retained under `java/benchmarks/build/reports/jmh/repeated`. Key repeated results: prepared generated Mortar active+id list measured 1,606.551 ops/s ± 25.299, 829.351 B/op ± 34.900, and mean 0.618 ms/op ± 0.001; reusable plain JDBC active+id list measured 1,609.769 ops/s ± 26.014, 829.325 B/op ± 34.248, and mean 0.616 ms/op ± 0.001. Prepared processor-generated Mortar `findById` list measured 1,600.546 ops/s ± 25.345, 836.672 B/op ± 33.595, and mean 0.613 ms/op ± 0.001; reusable plain JDBC `findById` list measured 1,617.934 ops/s ± 22.647, 838.895 B/op ± 38.218, and mean 0.614 ms/op ± 0.001. Result note: repeated evidence shows Mortar in the same throughput/latency band as direct JDBC, with strong allocation behavior, but does not support a broad public claim that Mortar is universally faster than maximum handwritten JDBC.
- Public-readiness performance report follow-up on 2026-06-01 added `docs/benchmarks/performance-report-2026-06-01.md` and updated `docs/benchmarks/postgres-execution-2026-06-01.md` with benchmark-readiness review. Decision: internal engineering baseline is ready; public performance claims remain blocked until the manual benchmark workflow runs on a clean commit, retained workflow artifacts are attached, release commit metadata is recorded, and independent reviewer sign-off is completed. Changed docs: `docs/benchmarks/performance-report-2026-06-01.md`, `docs/benchmarks/postgres-execution-2026-06-01.md`, `docs/benchmarks/README.md`, `docs/plan.md`, `docs/roadmap.md`.

### R11: LSP And VS Code Tooling

Status: Done

Goal: Make SQL transparency visible in the primary editor first while keeping the implementation editor-neutral.

Ordering note: On 2026-05-31, VS Code was confirmed as the primary Java editor path. Mortar therefore prioritizes LSP and VS Code before heavyweight IDE-specific plugins.

Slices:

- R11.1: LSP server foundation in Rust. Done on 2026-05-31.
- R11.2: Hover response for generated SQL. Done on 2026-05-31.
- R11.3: Diagnostics response for query warnings. Done on 2026-05-31.
- R11.4: Code action for copying generated SQL. Done on 2026-05-31.
- R11.5: VS Code extension wrapper. Done on 2026-05-31.
- R11.6: Run `EXPLAIN` action through configured PostgreSQL datasource. Done on 2026-06-01.
- R11.7: Neovim-compatible LSP documentation. Done on 2026-05-31.
- R11.8: Editor smoke-test matrix and screenshots for user-facing SQL transparency. Done on 2026-06-01.

Verification:

- LSP protocol tests.
- Sample VS Code workspace.
- Manual VS Code smoke test before claiming editor UX slices complete.

Current evidence:

- `cargo test -p mortar-lsp` passed on 2026-05-31 for the LSP capability contract. Changed modules/docs: `rust/crates/mortar-lsp`, `rust/Cargo.toml`, `rust/Cargo.lock`, `docs/lsp.md`, `README.md`. Dependency note: `lsp-server` 0.7.9 and `lsp-types` 0.97.0 were selected from crates.io on 2026-05-31 for a small stdio LSP foundation.
- `cargo test -p mortar-lsp` passed on 2026-05-31 for SQL snapshot hover rendering and `textDocument/hover` request routing through explicit `mortar:snapshot` markers. Changed modules/docs: `rust/crates/mortar-lsp`, `docs/lsp.md`. Source-map note: explicit markers are a transitional contract until Mortar adds generated Java source mappings.
- `cargo test -p mortar-lsp` passed on 2026-05-31 for metadata/schema diagnostics mapped to LSP diagnostic values and snapshot-marker diagnostics published from `didOpen`/`didChange` events. Changed modules/docs: `rust/crates/mortar-lsp`, `docs/lsp.md`.
- `cargo test -p mortar-lsp` passed on 2026-05-31 for the `mortar.copySql` code action contract and `textDocument/codeAction` request routing through explicit `mortar:snapshot` markers. Changed modules/docs: `rust/crates/mortar-lsp`, `docs/lsp.md`. Extension note: clipboard execution belongs to the editor client command, not the Rust LSP server.
- `bun install`, `bun run typecheck`, `bun run compile`, and `cargo test -p mortar-lsp` passed on 2026-05-31 for the VS Code extension wrapper. Changed modules/docs: `editors/vscode`, `.gitignore`, `docs/vscode.md`, `README.md`. Dependency note: `vscode-languageclient` 9.0.1 and `@types/vscode` 1.120.0 were selected from npm on 2026-05-31; TypeScript 5.6.3 is used because it satisfies Sequel's 5.6+ standard and avoids current type incompatibilities between newer TypeScript iterator definitions and the stable VS Code language client dependency graph.
- `cargo test -p mortar-lsp`, `bun run typecheck`, and `bun run compile` passed on 2026-05-31 for the PostgreSQL `EXPLAIN` editor action contract and LSP code-action request routing. Changed modules/docs: `rust/crates/mortar-lsp`, `editors/vscode`, `docs/lsp.md`, `docs/vscode.md`. Security note: VS Code owns local datasource configuration and calls the Mortar CLI; the LSP server does not store credentials or open database connections.
- `docs/neovim.md` was added on 2026-05-31 with direct stdio LSP launch guidance for Java buffers. Changed docs: `docs/neovim.md`, `README.md`. Scope note: Neovim command bindings for copy/EXPLAIN are intentionally deferred until the shared LSP command contract is exercised in a real client.
- `docs/editor-smoke-tests.md` and `examples/editor-smoke/vscode` were added on 2026-05-31 for R11.8 smoke planning. Changed docs/examples: `docs/editor-smoke-tests.md`, `examples/editor-smoke/vscode`, `README.md`.
- `bun run test` passed on 2026-06-01 in `editors/vscode`, launching an isolated VS Code 1.122.1 extension host with the smoke workspace. It verified Java-file activation, LSP startup through `LanguageClient.start()`, contributed `mortar.copySql` and `mortar.explainSql` commands, smoke workspace configuration for `mortar.lsp.path` and `mortar.cli.path`, SQL hover via `vscode.executeHoverProvider`, Mortar code actions via `vscode.executeCodeActionProvider`, snapshot diagnostics via `vscode.languages.getDiagnostics`, and clipboard behavior for `mortar.copySql`. Changed modules/docs: `editors/vscode`, `examples/editor-smoke/vscode`, `docs/editor-smoke-tests.md`, `docs/vscode.md`, `.gitignore`.
- `MORTAR_VSCODE_EXPLAIN_CONNECTION=postgres://postgres@localhost:55432/postgres bun run test` passed on 2026-06-01 in `editors/vscode` against Docker PostgreSQL 16 on port 55432, verifying the VS Code `mortar.explainSql` command through the configured datasource and Mortar CLI. Changed modules/docs: `editors/vscode`, `docs/editor-smoke-tests.md`, `docs/vscode.md`, `docs/roadmap.md`.
- `MORTAR_CAPTURE_SCREENSHOTS=1 MORTAR_VSCODE_SCREENSHOT_PORT=9333 MORTAR_VSCODE_EXPLAIN_CONNECTION=postgres://postgres@localhost:55432/postgres bun run test:screenshots` passed on 2026-06-01 in `editors/vscode` against Docker PostgreSQL 16 on port 55432. It generated verified VS Code screenshot evidence for SQL hover, source code actions, Problems diagnostics, and PostgreSQL EXPLAIN output in `docs/assets/editor-smoke/hover-sql.png`, `docs/assets/editor-smoke/code-actions.png`, `docs/assets/editor-smoke/diagnostics-problems.png`, and `docs/assets/editor-smoke/explain-output.png`. Changed modules/docs: `rust/crates/mortar-lsp`, `editors/vscode`, `docs/assets/editor-smoke`, `docs/editor-smoke-tests.md`, `docs/roadmap.md`. Editor note: R11 closes the primary VS Code workflow; IntelliJ remains a future secondary tooling path in R12.
- Reviewer follow-up on 2026-06-01 fixed cross-platform `file://` URI normalization and multi-root workspace snapshot routing in `rust/crates/mortar-lsp`; `cargo test -p mortar-lsp` passed with Unix absolute path, Windows drive path, and multi-root snapshot selection coverage.
- Reviewer re-check on 2026-05-31 confirmed the previous LSP routing and VS Code manifest findings are resolved. Residual risk remains around transitional `mortar:snapshot` marker routing until generated source maps land.
- Full local gates passed on 2026-06-01 after R11.1-R11.8: `gradlew.bat check`, `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, `cargo test`, `bun run typecheck`, and `bun run test`. The datasource-backed VS Code EXPLAIN smoke also passed on 2026-06-01 with `MORTAR_VSCODE_EXPLAIN_CONNECTION` against Docker PostgreSQL 16.

### R12: IntelliJ Tooling

Status: Done

Goal: Add IntelliJ support after the editor-neutral LSP path is useful and stable.

Slices:

- R12.1: IntelliJ plugin project. Done on 2026-06-01.
- R12.2: Hover generated SQL for a Mortar query expression. Done on 2026-06-01.
- R12.3: Navigate Java field to SQL column metadata. Done on 2026-06-01.
- R12.4: Copy generated SQL action. Done on 2026-06-01.
- R12.5: Run `EXPLAIN` action through configured datasource. Done on 2026-06-01.
- R12.6: Inline diagnostics from Mortar metadata. Done on 2026-06-01.
- R12.7: Quick fixes for safe rewrites when possible. Done on 2026-06-01.
- R12.8: Marketplace packaging. Done on 2026-06-01.

Verification:

- IntelliJ plugin tests where practical.
- Manual test matrix across supported IntelliJ versions.
- Screenshot docs for user-facing IDE features.

Current evidence:

- `gradlew.bat :editors:intellij:buildPlugin` passed on 2026-06-01 for the IntelliJ plugin project scaffold targeting IntelliJ IDEA 2026.1.2 with the bundled Java plugin. `gradlew.bat check` passed on 2026-06-01 with `editors:intellij` included in the root build. Changed modules/docs: `settings.gradle.kts`, `editors/intellij`, `docs/intellij.md`, `docs/adr/0003-intellij-plugin-adapter-boundary.md`, `docs/adr/index.md`, `README.md`, `.gitignore`, `docs/roadmap.md`. Dependency note: IntelliJ Platform Gradle Plugin 2.16.0 was selected from the Gradle Plugin Portal on 2026-06-01 because it is the latest published official JetBrains plugin; the plugin SDK documentation currently shows IntelliJ IDEA 2026.1.2 and bundled `com.intellij.java` as the Java plugin setup path. Architecture note: ADR-0003 keeps IntelliJ APIs confined to `editors/intellij`.
- `gradlew.bat :editors:intellij:test`, `gradlew.bat :editors:intellij:verifyPluginStructure`, `gradlew.bat :editors:intellij:buildPlugin`, and `gradlew.bat check` passed on 2026-06-01 for IntelliJ SQL hover support through a `DocumentationProvider` registered in `plugin.xml`. The provider reads the transitional `// mortar:snapshot=...` marker contract, finds the nearest `mortar.sql.snap.json`, parses the snapshot JSON, and renders escaped SQL documentation. Changed modules/docs: `editors/intellij`, `docs/intellij.md`, `docs/roadmap.md`. Test note: R12.2 has unit coverage for snapshot JSON lookup, documentation escaping, and marker parsing; IDE fixture/manual hover screenshots remain part of the broader R12 editor verification matrix.
- Reviewer follow-up on 2026-06-01 tightened IntelliJ snapshot parsing to fail closed on malformed JSON, invalid `format`, duplicate snapshot names, blank names, and blank SQL so the adapter matches the canonical SQL snapshot rules. `gradlew.bat :editors:intellij:test :editors:intellij:verifyPluginStructure :editors:intellij:buildPlugin` and `gradlew.bat check` passed after the fix.
- R12.3 navigation wiring was completed on 2026-06-01 through an IntelliJ `GotoDeclarationHandler` that opens the nearest `mortar.sql.snap.json` from a Java PSI element. `gradlew.bat :editors:intellij:test --stacktrace` passed with a `BasePlatformTestCase` fixture proving Java-source navigation targets the snapshot file. `gradlew.bat :editors:intellij:verifyPluginStructure :editors:intellij:buildPlugin` and `gradlew.bat check` passed after the fixture was added. Dependency note: IntelliJ platform fixture tests use the official `testFramework(TestFrameworkType.Platform)` helper and JUnit4 because the IntelliJ test framework integrates cleanly with that runner.
- R12.4 copy-generated-SQL wiring was completed on 2026-06-01 through an IntelliJ editor popup action backed by the same shared PSI SQL lookup used by hover. `gradlew.bat :editors:intellij:test :editors:intellij:verifyPluginStructure :editors:intellij:buildPlugin` passed with a `BasePlatformTestCase` fixture proving Java caret lookup resolves the generated SQL from `mortar.sql.snap.json`. Changed modules/docs: `editors/intellij`, `docs/roadmap.md`.
- R12.5 PostgreSQL EXPLAIN wiring was completed on 2026-06-01 through an IntelliJ editor popup action that reuses the shared PSI SQL lookup, reads `dev.mortar.cli.path` and `dev.mortar.postgres.connection`, and executes `mortar explain --connection ... --sql ...` through a background CLI process. `gradlew.bat :editors:intellij:test :editors:intellij:verifyPluginStructure :editors:intellij:buildPlugin` passed with command-building unit coverage. Architecture note: IntelliJ does not open database connections directly; database access remains delegated to Mortar CLI.
- R12.6 inline diagnostics were completed on 2026-06-01 through a Java `Annotator` registered in `plugin.xml`. The annotator warns when a `// mortar:snapshot=...` marker has no nearest snapshot file, points at a missing snapshot entry, or resolves through malformed/non-canonical snapshot JSON. `gradlew.bat :editors:intellij:test --no-daemon` passed with `BasePlatformTestCase` coverage for missing-file diagnostics, missing-entry diagnostics, and rendered warning highlights. Correctness note: marker lookup now only associates markers from the current method lead-in or direct PSI siblings, and a fixture proves a previous method marker is not reused.
- R12.7 quick fixes were completed on 2026-06-01 through an IntelliJ intention attached only to the missing-snapshot-file diagnostic. The quick fix creates a canonical empty `mortar.sql.snap.json` next to the current Java source file and deliberately does not invent placeholder SQL for missing entries. `gradlew.bat :editors:intellij:test --no-daemon` passed with fixture coverage for quick-fix availability and file creation.
- R12.8 Marketplace packaging was completed on 2026-06-01 by moving plugin description/change notes into versioned `editors/intellij/marketplace` HTML files and configuring `publishPlugin` through the official IntelliJ Platform Gradle Plugin `publishing` DSL with `PUBLISH_TOKEN`. `gradlew.bat :editors:intellij:test :editors:intellij:verifyPluginStructure :editors:intellij:buildPlugin --no-daemon` passed and produced the local plugin ZIP under `editors/intellij/build/distributions`. Publishing is token-gated and was not executed locally.
- R12 final root verification passed on 2026-06-01 with `gradlew.bat check --no-daemon`.

### R13: Documentation And Examples

Status: Done

Goal: Make Mortar understandable without private Sequel context.

Slices:

- R13.1: Getting started guide. Done on 2026-06-01.
- R13.2: Spring Boot PostgreSQL example. Done on 2026-06-01.
- R13.3: DDD/Clean Architecture example. Done on 2026-06-01.
- R13.4: Migration guide from Spring Data `@Query`. Done on 2026-06-01.
- R13.5: Comparison guide: Mortar vs JPA Criteria vs QueryDSL vs jOOQ. Done on 2026-06-01.
- R13.6: Troubleshooting guide. Done on 2026-06-01.
- R13.7: Performance guide. Done on 2026-06-01.
- R13.8: Public API reference. Done on 2026-06-01.

Verification:

- Every guide must have runnable code.
- Examples compile in CI.
- Docs avoid Sequel-internal assumptions.

Current evidence:

- R13.1-R13.8 documentation was completed on 2026-06-01 through public docs linked from `README.md`: `docs/getting-started.md`, `docs/spring-boot-postgres-example.md`, `docs/ddd-clean-architecture-example.md`, `docs/migration-from-spring-data-query.md`, `docs/comparison.md`, `docs/troubleshooting.md`, `docs/performance.md`, and `docs/api-reference.md`.
- R13.2 runnable example was completed on 2026-06-01 as the Gradle module `examples:spring-boot-postgres`. The module compiles an annotated `Client` model through the Mortar annotation processor, uses generated `QClient` query metadata from `ClientRepository`, and tests PostgreSQL SQL rendering plus repository boundary behavior.
- `gradlew.bat :examples:spring-boot-postgres:test --no-daemon` and `gradlew.bat :examples:spring-boot-postgres:check --no-daemon` passed on 2026-06-01. The example is included in `settings.gradle.kts`, so root `gradlew.bat check` compiles it in CI.
- R13 final root verification passed on 2026-06-01 with `gradlew.bat check --no-daemon`.
- Generated executor usage follow-up on 2026-06-01 updated `examples/spring-boot-postgres` so `ClientRepository.findById(...)` uses `QClient.CLIENT.findById(renderer)` and `MortarJdbcClient.fetchOptional(...)`, while `findActiveById(...)` remains a DSL example for multi-predicate transparent SQL. `gradlew.bat :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon` and `gradlew.bat check --no-daemon` passed with SQL transparency and repository-boundary coverage. Changed module/docs: `examples/spring-boot-postgres`, `docs/spring-boot-postgres-example.md`, `docs/plan.md`.

### R14: Release And Governance

Status: Done

Goal: Make Mortar sustainable as a public project.

Slices:

- R14.1: Apache-2.0 or MIT license decision. Done on 2026-05-31.
- R14.2: Contribution guide. Done on 2026-05-31.
- R14.3: Code of conduct. Done on 2026-05-31.
- R14.4: Security policy. Done on 2026-05-31.
- R14.5: Semantic versioning policy. Done on 2026-06-01.
- R14.6: Maven Central publishing. Done on 2026-06-01.
- R14.7: GitHub releases with changelog. Done on 2026-06-01.
- R14.8: Rust crate publishing policy. Done on 2026-06-01.

Verification:

- Dry-run publication in CI.
- Signed artifacts when release process is mature.
- Release checklist followed for every public release.

Current evidence:

- R14.1-R14.4 governance docs exist as `LICENSE`, `NOTICE`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, and `SECURITY.md`; Apache-2.0 is recorded in `docs/adr/0002-apache-2-license.md`.
- R14.5-R14.8 release policy was completed on 2026-06-01 in `docs/release.md`. The policy covers pre-1.0 and post-1.0 versioning, Maven Central scope, GitHub release notes, and Rust crate publication order.
- Maven publishing is restricted to public Java library modules only: `java:core`, `java:dialect-postgres`, `java:runtime-jdbc`, `java:spring-boot-starter`, `java:processor`, and `java:testkit`. Examples, benchmarks, aggregate projects, and editor plugins are excluded from Maven Central publication.
- CI release dry-run was added on 2026-06-01 in `.github/workflows/ci.yml` after Java and Rust gates. It runs `./gradlew publishToMavenLocal` and `cargo publish --dry-run -p mortar-compiler` from the repository root paths.
- Maven Central publishing follow-up on 2026-06-01 aligned Mortar with the public release workflow while using the current Vanniktech Maven Publish Plugin 0.36.0. Public Java artifacts now publish under group `io.github.sequelcore` with artifact IDs `mortar-core`, `mortar-dialect-postgres`, `mortar-runtime-jdbc`, `mortar-spring-boot-starter`, `mortar-processor`, and `mortar-testkit`. `.github/workflows/publish.yml` publishes on semantic version tags `v*`, reads Maven Central and GPG secrets from CI-provided environment variables, runs `./gradlew publishToMavenCentral --no-daemon --no-configuration-cache`, and creates a GitHub release. Verification passed locally with `gradlew.bat verifyPublishWorkflow --no-daemon` and `gradlew.bat publishToMavenLocal --no-daemon --no-configuration-cache`. Signing is conditional locally so Maven local dry-runs work without a private key; release publishing signs artifacts when CI injects `ORG_GRADLE_PROJECT_signingInMemoryKey`.
- Local release dry-run verification passed on 2026-06-01 with `gradlew.bat publishToMavenLocal --no-daemon --no-configuration-cache` and `cargo publish --dry-run -p mortar-compiler --allow-dirty`. The Rust command used `--allow-dirty` locally because this workspace has uncommitted implementation changes; CI runs without that flag on a clean checkout.
- Rust verification passed on 2026-06-01 with `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test`.
- Java root verification passed on 2026-06-01 with `gradlew.bat check --no-daemon`.

### R15: Public API Readiness Hardening

Status: Done

Goal: raise Mortar from public project infrastructure to a credible public API
surface for real Java/Spring projects before any release is tagged.

Slices:

- R15.1: Generate common read executors beyond primary-key lookup. Status: Done.
- R15.2: Stabilize Java-first repository API ergonomics for common real queries. Status: Done.
- R15.3: Harden Spring Boot starter ergonomics, properties, diagnostics, and examples. Status: Done.
- R15.4: Expand PostgreSQL dialect coverage with syntax and Testcontainers evidence. Status: Done.
- R15.5: Strengthen processor/codegen diagnostics and generated-source documentation. Status: Done.
- R15.6: Upgrade public documentation from examples to real usage guidance. Status: Done.
- R15.7: Broaden benchmarks to joins, pages, writes, and tuned PgJDBC scenarios. Status: Done.
- R15.8: Add more end-to-end examples that compile in CI. Status: Done.
- R15.9: Finalize pre-1.0 semantic-versioning and compatibility policy. Status: Done.
- R15.10: Review and complete Javadocs for public API before first release. Status: Done.

Verification:

- Every slice requires tests first when behavior changes.
- Every generated API change must compile inside a real example module.
- Documentation updates must avoid private Sequel workspace assumptions.
- Benchmark claims remain internal until CI artifacts and reviewer sign-off exist.

Current evidence:

- R15.1 generated `findAll(renderer)` on `Q*` metamodels alongside existing
  `findById(renderer)`. The generated API exposes `FindAllRow`, direct SQL
  access, metadata, empty parameter type metadata, no-op explicit binding, and
  projection-index row mapping through `MortarGeneratedQuery<P, T>`.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesFindAllExecutorForAnnotatedEntity --no-daemon`
  failed first on 2026-06-01, proving the missing generated executor.
- `gradlew.bat :java:processor:test --no-daemon` passed on 2026-06-01 after
  implementing generated `findAll`.
- `gradlew.bat :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon`
  passed on 2026-06-01 with `ClientRepository.findAll()` using
  `QClient.CLIENT.findAll(renderer)` and `MortarJdbcClient.fetch(...)`.
- Changed modules/docs: `java/processor`, `examples/spring-boot-postgres`,
  `docs/api-reference.md`, `docs/getting-started.md`,
  `docs/spring-boot-postgres-example.md`, `docs/plan.md`, and
  `docs/roadmap.md`.
- Architecture note: generated metamodels still do not render SQL themselves;
  generated read executors pre-render through a caller-provided
  `QueryRenderer`, and JDBC execution remains in `java/runtime-jdbc`.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.2 added `MortarNoParameters` plus `MortarJdbcClient.fetch(query)` and
  `fetchOptional(query)` overloads for generated no-parameter queries. The
  processor-generated `findAll` executor now implements
  `MortarGeneratedQuery<MortarNoParameters, FindAllRow>`, so repository code can
  use `jdbcClient.fetch(QClient.CLIENT.findAll(renderer))` without constructing
  empty parameter records.
- `gradlew.bat :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcClientTest.executesGeneratedQueryWithoutParameters --no-daemon`
  failed first on 2026-06-01 because `MortarNoParameters` and the overload did
  not exist, then passed after implementation.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesFindAllExecutorForAnnotatedEntity --no-daemon`
  failed first on 2026-06-01 while generated `findAll` still used
  `FindAllParameters`, then passed after implementation.
- Changed modules/docs: `java/runtime-jdbc`, `java/processor`,
  `examples/spring-boot-postgres`, `docs/api-reference.md`,
  `docs/getting-started.md`, `docs/spring-boot-postgres-example.md`,
  `docs/plan.md`, and `docs/roadmap.md`.
- Architecture note: this is a runtime API convenience for generated queries
  only; SQL rendering remains in dialects and execution remains in JDBC runtime.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.3 added an explicit `mortar.dialect` starter property with PostgreSQL as
  the only supported value, kept renderer selection inside
  `java/spring-boot-starter`, and expanded the actuator diagnostics descriptor
  to report dialect, SQL format, JDBC logging, diagnostics, and renderer class.
- `gradlew.bat :java:spring-boot-starter:test --tests dev.mortar.spring.MortarAutoConfigurationTest.appliesDialectPropertyToPostgresRenderer --tests dev.mortar.spring.MortarDiagnosticsEndpointTest.exposesMortarDiagnosticsEndpointWhenDiagnosticsAreEnabled --tests dev.mortar.spring.MortarDiagnosticsEndpointTest.exposesConfiguredStarterDiagnostics --no-daemon`
  failed first on 2026-06-01 because `MortarDialect`, the bound dialect
  property, and the richer diagnostics descriptor did not exist, then passed
  after implementation.
- Changed modules/docs: `java/spring-boot-starter`,
  `examples/spring-boot-postgres`, `docs/spring-boot-postgres-example.md`,
  `docs/spring-boot-compatibility.md`, `docs/plan.md`, and
  `docs/roadmap.md`.
- Architecture note: the new dialect property is starter wiring only; core,
  runtime, and PostgreSQL dialect boundaries did not change.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.4 added PostgreSQL 16 Testcontainers coverage for an explicitly
  paginated join query and UUID array overlap execution, broadening syntax
  evidence beyond single-table lookup shapes.
- `gradlew.bat :java:dialect-postgres:test --tests dev.mortar.postgres.PostgresReadSyntaxIntegrationTest --tests dev.mortar.postgres.PostgresSpecificPredicateIntegrationTest.executesUuidArrayOverlapAgainstPostgreSQL --no-daemon`
  passed on 2026-06-01.
- Changed module/docs: `java/dialect-postgres` and `docs/roadmap.md`.
- Architecture note: no renderer contract changed; this slice adds executable
  PostgreSQL evidence for existing DSL and PostgreSQL predicate behavior.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.5 added fail-fast annotation processor diagnostics for invalid SQL table,
  alias, and column identifiers, using stable public codes
  `MORTAR_PROCESSOR_005`, `MORTAR_PROCESSOR_006`, and
  `MORTAR_PROCESSOR_007`. Generated `findAll` and `findById` source now emits
  Javadocs that describe the SQL shape and dialect-renderer boundary.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorDiagnosticsTest.rejectsInvalidSqlMetadata --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesFindByIdExecutorForAnnotatedEntity --tests dev.mortar.processor.MortarProcessorGenerationTest.generatesFindAllExecutorForAnnotatedEntity --no-daemon`
  failed first on 2026-06-01 because invalid SQL metadata was accepted and the
  generated executor Javadocs were missing, then passed after implementation.
- Changed modules/docs: `java/processor`, `docs/api-reference.md`,
  `docs/diagnostics.md`, and `docs/roadmap.md`.
- Architecture note: this slice only strengthens compile-time validation and
  generated-source documentation; generated queries still render through a
  caller-provided `QueryRenderer` and execute through JDBC runtime contracts.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- Reviewer follow-up for R15.5 added blank relation target-column validation so
  `@MortarRelation(targetColumn = " ")` fails fast instead of generating a
  malformed join path.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorDiagnosticsTest.rejectsBlankRelationTargetColumn --no-daemon`
  failed first on 2026-06-01, then passed after validation was tightened.
- R15.8 added `examples:clean-architecture-postgres`, a CI-compiling example
  that keeps the domain-facing `ClientReader` port free of Mortar types while
  the PostgreSQL infrastructure adapter uses generated `findById` and a
  paginated DSL query. The module is included in `settings.gradle.kts`, so root
  Java verification compiles it in CI.
- `gradlew.bat :examples:clean-architecture-postgres:check --no-daemon` failed
  first on 2026-06-01 because the `PostgresClientReader` adapter did not exist,
  then passed after implementation.
- Changed modules/docs: `examples/clean-architecture-postgres`,
  `settings.gradle.kts`, `README.md`, `docs/ddd-clean-architecture-example.md`,
  and `docs/roadmap.md`.
- Architecture note: this is an example-only module; it adds no new runtime,
  Spring, or core abstraction.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.7 broadened `PostgresExecutionBenchmark` with joined paginated reads,
  update batches, and benchmark-local tuned PgJDBC scenarios using
  `prepareThreshold=1`, `preparedStatementCacheQueries=256`, and
  `binaryTransfer=true`.
- `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`
  failed first on 2026-06-01 because the expanded benchmark methods and tuned
  PgJDBC metadata did not exist, then passed after implementation.
- Changed modules/docs: `java/benchmarks`, `docs/benchmarks/README.md`,
  `docs/performance.md`, and `docs/roadmap.md`.
- Evidence note: this slice expands the benchmark matrix only. It does not make
  a public performance claim; public claims still require retained JMH JSON
  artifacts and reviewer sign-off.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.6 added `docs/usage-guide.md`, covering when to use generated executors,
  Java DSL queries, pre-rendered queries, Spring Boot starter properties,
  Clean Architecture placement, adapter-boundary SQL tests, Testcontainers, JMH
  evidence, and diagnostics. The guide is linked from `README.md` and
  `docs/getting-started.md`.
- Verification note: this is a documentation-only slice; executable examples
  are covered by `gradlew.bat :examples:spring-boot-postgres:test --no-daemon`
  and `gradlew.bat :examples:clean-architecture-postgres:check --no-daemon`
  in the final gate sequence.
- Changed docs: `docs/usage-guide.md`, `README.md`,
  `docs/getting-started.md`, and `docs/roadmap.md`.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.9 expanded `docs/release.md` with the explicit pre-1.0 public
  compatibility surface, required migration-note contents, current tested
  compatibility envelope, and unsupported/future compatibility wording rules.
  `CHANGELOG.md` now records R15 public API readiness hardening under
  `Unreleased`.
- Verification note: this is a policy-only slice; final release workflow
  validation remains covered by `gradlew.bat check --no-daemon` and the
  configured release dry-run CI job.
- Changed docs: `docs/release.md`, `CHANGELOG.md`, and `docs/roadmap.md`.
- Release note: no Maven Central or GitHub release is authorized by this slice.
- R15.10 added concise Javadocs to handwritten public Java types across
  `java/core`, `java/dialect-postgres`, `java/runtime-jdbc`,
  `java/spring-boot-starter`, `java/processor`, and `java/testkit`, and kept
  generated executor Javadocs aligned with the generated JDBC contract.
- `gradlew.bat javadoc --no-daemon` passed on 2026-06-01.
- Changed modules/docs: `java/core`, `java/dialect-postgres`,
  `java/runtime-jdbc`, `java/spring-boot-starter`, `java/processor`,
  `java/testkit`, `docs/api-reference.md`, and `docs/roadmap.md`.
- Release note: no Maven Central or GitHub release is authorized by this slice.

### R16: Java-First Ergonomics And Query Authoring

Status: Done

Goal: let a Java user write bounded fixed-shape read queries with less friction
than handwritten SQL plus JDBC binding and row mapping, while preserving strong
autocomplete, early errors, and visible SQL.

Scope:

- generated fixed single-table read facades for `findById` and `findAll`;
- repository row-to-DTO mapping for those read shapes, with generated facade
  projections deferred;
- bound-parameter and visible-SQL runtime/testkit contract;
- one generated read namespace per entity with a strict public API budget;
- query-id/source-map metadata contract for future editor tooling;
- repository and service examples that keep Mortar inside infrastructure
  adapters;
- no hidden ORM behavior;
- no loss of SQL transparency.

R16 slices:

- R16.1: Contract, ADR, and API budget. Status: Done.
- R16.2: Fixed single-table read facades. Status: Done.
- R16.3: Bound parameters, visible SQL, and testkit contract. Status: Done.
- R16.4: Examples and usage guidance. Status: Done.

Non-goals:

- no optional-filter overload matrices;
- no joins, relation read models, `count`, `exists`, stable page, generated
  writes, or batches;
- no generated repository classes or Spring Data-style method derivation;
- no generated query object execution methods such as
  `query.fetchOptional(jdbcClient)`;
- no custom LSP completion engine;
- no migration of existing Sequel apps;
- no release, tag, publication, merge, or push implied by this planning slice.

Architecture debate outcome:

- An xhigh architecture challenge narrowed R16 from broad query authoring to
  fixed single-table read ergonomics. Optional filters, joins, richer
  projections, `count`, `exists`, writes, and batches must prove value in R17's
  public real-query corpus before entering the product surface.
- Generated facade API growth is the primary risk, so R16 has an explicit API
  budget: one generated read namespace per entity, a fixed read method set, no
  overload explosion, and no self-executing generated query objects.
- Source-map-backed SQL hover/navigation is realistic only after the metadata
  contract exists and is hardened against generated sources; R16 defines the
  contract, while R18 owns editor hardening.
- Clean Architecture remains mandatory: generated query plans expose SQL and
  metadata, but `java/runtime-jdbc` executes them and domain ports stay
  Mortar-free.

Exit criteria:

- public generated API growth stays within the approved budget;
- fixed read examples compile and keep SQL visible through tests/snapshots;
- generated query plans expose SQL, parameter types, rendered metadata, and
  testkit inputs;
- processor-generated source still calls a supplied `QueryRenderer` and does
  not render SQL itself;
- `MortarJdbcClient` remains the execution boundary;
- no ORM-like behavior is introduced.

Current evidence:

- R16.1 added ADR-0005, `MortarBoundQuery<T>` in `java/core`,
  `MortarJdbcBoundQuery<T>` in `java/runtime-jdbc`, optional query metadata
  parsing in `rust/crates/mortar-compiler`, processor query-id/generated-source
  metadata for existing generated `findAll` and `findById` executor shapes,
  and testkit SQL assertions for `MortarBoundQuery<?>`.
- R16.1 deliberately did not generate the R16.2 `Read` facade namespace and
  used a negative processor guard until the facade shape existed.
- R16.2 generated one `Q*.Read` namespace per entity with
  `read(renderer).findById(id)` and `read(renderer).findAll()` returning
  immutable `MortarBoundQuery<FindByIdRow>` and
  `MortarBoundQuery<FindAllRow>` values. The facade renders through the
  supplied `QueryRenderer`, exposes SQL, parameters, parameter types, metadata,
  and row type through the R16.1 bound-query contract, and has no execution,
  write, optional-filter, relation, `count`, or `exists` methods.
- R16.2 added copy-style `.named(...)` to `MortarBoundQuery<T>` and
  `MortarJdbcBoundQuery<T>` and added explicit `MortarJdbcClient` execution
  overloads for bound queries. Execution remains in `java/runtime-jdbc`; query
  objects still do not execute themselves.
- R16.2 updated processor generated-source metadata to point canonical fixed
  read entries at `read.findById` and `read.findAll` on `Q*.Read` while keeping
  query IDs and snapshot keys stable.
- R16.2 architecture debate outcome: use singular `Read`, keep
  `read(renderer)` on each generated metamodel, avoid JDBC leakage from the
  generated facade, keep explicit `findAll()` for reference-data reads, put
  immutable `.named(...)` on the returned bound query, defer projection support,
  and include only the narrow `MortarJdbcClient` bound-query bridge needed to
  make repository code shorter than R15.
- Focused R16.2 verification on 2026-06-02: `gradlew.bat :java:core:test
  --tests dev.mortar.core.MortarBoundQueryTest :java:runtime-jdbc:test --tests
  dev.mortar.jdbc.MortarJdbcBoundQueryTest --tests
  dev.mortar.jdbc.MortarJdbcClientTest :java:processor:test --tests
  dev.mortar.processor.MortarProcessorGenerationTest
  :examples:spring-boot-postgres:test --tests
  dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon` failed
  first because `MortarBoundQuery.named(String)` did not exist, then passed
  after implementation.
- Focused metadata verification on 2026-06-02:
  `gradlew.bat :java:processor:test --tests
  dev.mortar.processor.MortarProcessorMetadataFileTest --no-daemon` passed
  after metadata was updated for the `Read` facade.
- Final R16.2 verification on 2026-06-02 passed:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`,
  and `cargo test`; from `editors/vscode`, `bun run typecheck`; `git diff --check`;
  and the private path scrub excluding build outputs and caches.
- Changed modules/docs: `java/core`, `java/runtime-jdbc`, `java/processor`,
  `java/testkit`, `rust/crates/mortar-compiler`, `docs/adr`,
  `docs/api-reference.md`, `docs/metadata.md`, `docs/lsp.md`,
  `docs/testkit.md`, `docs/spec/architecture.md`, `docs/plan.md`, and
  `docs/roadmap.md`.
- Architecture note: core owns rendered query inspection, runtime-jdbc owns JDBC
  row mapping/execution, the processor still does not render SQL, and generated
  query objects still do not execute themselves.
- Release note: no release, tag, publication, merge, push, or application
  migration is authorized by this slice.
- R16.3 strengthened the public testkit contract for `MortarBoundQuery<?>` by
  adding query name and row type context to bound-query assertion failures while
  keeping the assertion API limited to SQL text, parameter values, parameter
  types, and metadata. The Spring example repository test now asserts generated
  facade SQL through `assertThatSql(query)`, proves
  `read(renderer).findById(7L)` exposes the supplied identifier as the rendered
  parameter, proves `Long.class` parameter type visibility, checks metadata and
  row type, and verifies null boxed identifiers fail before SQL rendering.
- R16.3 architecture debate outcome: keep `MortarBoundQuery<T>` as the sole
  framework-free visible-SQL contract, keep `RenderedQuery` as the lower-level
  renderer fallback, reject generated self-execution and any second visible-SQL
  abstraction, and leave runtime execution adapter-owned in `MortarJdbcClient`.
- Focused R16.3 verification on 2026-06-02:
  `gradlew.bat :java:testkit:test --tests
  dev.mortar.testkit.MortarSqlAssertionsTest --no-daemon` failed first because
  bound-query assertion failures did not include query name and row type, then
  passed after implementation. `gradlew.bat
  :examples:spring-boot-postgres:test --tests
  dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon` passed
  for generated facade SQL, parameter value, parameter type, metadata, row
  type, and null-id boundary behavior.
- Final R16.3 verification on 2026-06-02 passed:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`; `git diff --check`;
  and the private path scrub excluding build outputs and caches.
- Changed modules/docs: `java/testkit`, `examples/spring-boot-postgres`,
  `docs/api-reference.md`, `docs/getting-started.md`, `docs/plan.md`,
  `docs/roadmap.md`, `docs/spring-boot-postgres-example.md`,
  `docs/testkit.md`, and `docs/usage-guide.md`.
- Architecture note: no generated execution methods, repositories, joins,
  optional filters, writes, counts, exists queries, JDBC dependencies in
  `java/core`, or editor source-map hardening were added.
- Release note: no release, tag, publication, merge, push, or application
  migration is authorized by this slice.
- R16.4 completed the public examples and usage guidance convergence for the
  shipped R16 surface. The Clean Architecture example now uses
  `CLIENT.read(renderer).findById(id).named("PostgresClientReader.findById")`,
  executes through `jdbcClient.fetchOptional(query)`, maps generated rows to
  the domain-facing `ClientSummary`, and asserts SQL drift with
  `assertThatSql(query).hasSql(...).hasParameters(...).hasParameterTypes(...)`.
- R16.4 architecture debate outcome: the xhigh challenge approved a narrow
  docs/examples convergence slice, required removing the legacy generated
  executor path from the Clean Architecture example, and kept
  `MortarGeneratedQuery` as an advanced hot-path contract rather than the
  canonical repository flow. Generated projections, optional filters, joins,
  writes, `count`, `exists`, generated repositories, self-executing query
  objects, and LSP/source-map hardening remain outside R16.
- Focused R16.4 verification on 2026-06-02:
  `gradlew.bat :examples:clean-architecture-postgres:test --tests
  dev.mortar.examples.cleanpostgres.PostgresClientReaderTest --no-daemon`
  failed first because the adapter still used `CLIENT.findById(renderer)` with
  `FindByIdParameters`, then passed after the adapter moved to the generated
  `Read` facade. `gradlew.bat :examples:spring-boot-postgres:test --tests
  dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon` and
  `gradlew.bat :examples:clean-architecture-postgres:check --no-daemon` passed.
- Final R16.4 verification on 2026-06-02 passed:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`; `git diff --check`;
  and the private path scrub excluding build outputs and caches.
- Changed modules/docs: `examples/clean-architecture-postgres`,
  `docs/api-reference.md`, `docs/ddd-clean-architecture-example.md`,
  `docs/examples/spring-clean-architecture-repository.md`,
  `docs/getting-started.md`, `docs/plan.md`, `docs/roadmap.md`,
  `docs/spring-boot-postgres-example.md`, and `docs/usage-guide.md`.
- R16 exit criteria are satisfied: generated API growth remains within
  ADR-0005, fixed read examples compile, SQL and parameter types remain visible
  through testkit assertions, generated source renders through supplied
  `QueryRenderer`, `MortarJdbcClient` remains the execution boundary, and no
  ORM-like behavior was introduced.
- Release note: no release, tag, publication, merge, push, or application
  migration is authorized by this slice.

### R17: Real-Query Coverage Gate

Status: Done

Goal: prove broader read-query ergonomics against realistic public query
fixtures before adding more generated API or considering any existing
application migration.

Scope:

- public non-application-specific mini-domain and query corpus;
- realistic repository/service flows that keep Mortar inside infrastructure
  adapters;
- optional-filter searches;
- explicit joins through generated relationship metadata;
- stable pagination that requires deterministic ordering;
- `count` and `exists` use cases evaluated as scalar-query decisions;
- DTO/record projections and richer read-model projections;
- multi-predicate reads;
- repository-level SQL drift tests;
- snapshot/testkit expectations;
- compile-time and refactor failure cases for renamed or deleted fields.

R17 slices:

- R17.1: Fixture app and query corpus contract. Status: Done.
- R17.2: DSL-first corpus implementation. Status: Done.
- R17.3: Optional filter decision. Status: Done.
- R17.4: Joins, projections, pagination, count, and exists decisions. Status:
  Done.
- R17.5: R18 fixture handoff and roadmap update. Status: Done.

Constraint:

- if a capability is not needed by the realistic query corpus, it should not
  enter R17.
- R17 must prove broader query shapes are actually simpler than the current DSL
  or handwritten SQL plus JDBC binding and row mapping before expanding the
  generated facade surface.
- default to DSL-first evaluation; generated API support requires repeated
  fixture evidence and a bounded public method set.
- reject generated optional-filter overload matrices, generated repositories,
  self-executing query objects, implicit relation traversal, aggregate loading,
  and generated writes or batches.
- do not use private application code or disguised private migrations as the
  fixture source.

API decision rules:

- Generate only repeated query families that appear across multiple public
  fixture use cases and are materially shorter or safer than the explicit DSL.
- Keep generated APIs inspection-only and framework-free; execution remains in
  `MortarJdbcClient` or another adapter.
- Keep domain and application ports Mortar-free.
- Require explicit ordering before paged execution.
- Require explicit join paths and join types; no implicit aggregate loading or
  lazy traversal.
- Treat `count` and `exists` as ADR-sized decisions because scalar results may
  reopen the visible-query and runtime execution contracts.

Exit criteria:

- public fixture corpus exists and compiles in CI;
- each corpus query has a named repository use case and adapter-boundary SQL
  drift test;
- canonical corpus queries have snapshot/testkit expectations;
- optional filters, joins, projections, stable pagination, `count`, and
  `exists` each have explicit generated/DSL/defer/reject decisions;
- no generated API expands without evidence-backed decision records;
- Clean Architecture boundaries are verified;
- R18 receives a concrete fixture handoff for tooling and refactor-safety
  hardening.

R17 completion record:

- Added public fixture modules `examples:query-corpus-domain`,
  `examples:query-corpus-application`, and
  `examples:query-corpus-infrastructure-postgres`.
- Added the neutral service-ticket corpus with four annotated entities
  (`TicketRecord`, `CustomerRecord`, `TechnicianRecord`, and
  `TicketStatusRecord`) and explicit relation metadata for customer,
  assigned-technician, and status joins.
- Added realistic Mortar-free application ports and adapter-owned PostgreSQL
  query flows for fixed lookup, reference reads, optional-filter search,
  explicit join projections, multi-predicate reads, ordered pages, and
  unordered-page diagnostic evidence.
- Added canonical snapshot inventory at
  `examples/query-corpus-infrastructure-postgres/src/test/resources/r17-query-corpus/mortar.sql.snap.json`.
- Added `docs/r17-query-corpus.md` as the R18 handoff covering stable query
  names, snapshot keys, expected SQL, parameter and metadata expectations,
  refactor failure cases, schema drift cases, and editor/source-map needs.
- Accepted ADR-0006. R17 made no generated API expansion. Optional filters,
  multi-predicate reads, joins, projections, and stable pagination remain
  DSL-first; `count` and `exists` are deferred scalar-contract decisions;
  generated optional-filter overload matrices, repositories, writes, batches,
  self-executing query objects, implicit traversal, aggregate loading, lazy
  loading, identity maps, and ORM behavior are rejected.
- Focused verification passed on 2026-06-02:
  `gradlew.bat :examples:query-corpus-domain:test :examples:query-corpus-application:test :examples:query-corpus-infrastructure-postgres:test --no-daemon`.
- Final verification passed on 2026-06-02: `gradlew.bat check --no-daemon`;
  from `rust`, `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`;
  `git diff --check`; and the private path/private project scrub excluding
  build outputs and caches.

### R18: Stability, Refactor Safety And Tooling Hardening

Status: Done

Goal: harden the R16/R17 surface and the R17 fixture corpus against stale
generated code, stale metadata, refactor failures, snapshot drift, editor
misreporting, and Gradle multi-module workflow gaps before real projects depend
on it.

Scope:

- contract definition for what `refactor-safe` means in Mortar;
- R17 fixture baseline/fail/recover cases for renamed fields, deleted fields,
  deleted relations, changed column metadata, stale generated metadata, and
  recovery updates;
- compatibility checks over publishable handwritten Java APIs and documented
  file formats such as `mortar-metadata-v1` and `mortar-sql-snapshot-v1`;
- generator golden tests for fixed reads, relation metadata, query IDs,
  source-map entries, and snapshot keys;
- Gradle clean-build and incremental-build convergence across the
  domain/application/infrastructure fixture modules;
- explicit verification of the processor incremental-classification risk where
  shared metadata output must not leave stale state;
- source-map-backed SQL hover and copy SQL for generated fixed-read calls in
  the R17 fixture, specifically `TicketReader.findHeader` through generated
  `read.findById` metadata and `TicketReader.listStatusOptions` through
  generated `read.findAll` metadata;
- stale metadata/source-map/snapshot diagnostics that fail closed before
  misleading editor output;
- VS Code as the primary editor proof path through the shared LSP contract;
- IntelliJ only as a secondary thin-adapter check if it does not expand scope;
- schema drift workflow as a separate CLI/tooling diagnostic path;
- SQL snapshots as a normal developer workflow.

R18 concrete guarantees:

- renamed entity fields used by generated reads or DSL query code cause
  compile-time or processor diagnostic failure while consumers remain stale, or
  regenerate consistent `Q*`, metadata, source-map, and snapshot state after
  consumers are updated;
- deleted fields, deleted relation metadata, or deleted query metadata inputs
  fail deterministically before runtime execution;
- changed column metadata changes SQL snapshot/test expectations when rendered
  SQL changes;
- stale metadata is detected before hover, navigation, or copy SQL presents old
  output as current;
- generated Java source maps connect generated fixed-read facade symbols,
  query IDs, snapshot keys, row types, parameters, stable source anchors, and
  freshness fingerprints. Rendered SQL evidence remains in SQL snapshots;
- VS Code hover/copy SQL, EXPLAIN code actions, snapshot-entry definition
  navigation, and stale-source-map diagnostics use source-map-backed data for
  generated fixed-read calls;
- domain and application fixture modules remain Mortar-free;
- multi-module Gradle behavior remains correct for clean and incremental
  builds.

R18 slices:

- R18.1: Refactor-safety fixture contract. Status: Done.
- R18.2: R17 refactor failure matrix. Status: Done.
- R18.3: Generated metadata and source-map contract hardening. Status:
  Done.
- R18.4: Gradle incremental and multi-module verification. Status: Done.
- R18.5: VS Code source-map hover and copy SQL hardening. Status: Done.
- R18.6: Schema drift, completion review, and R19 handoff. Status: Done.

Non-goals:

- no private application migration;
- no public release, release candidate, tag, publication, or performance claim;
- no generated API expansion beyond the R16 fixed-read surface;
- no generated optional-filter API or optional-filter overload matrices;
- no generated joins, projections, repositories, writes, or batches;
- no `count` or `exists` scalar contract unless separately ADR-approved;
- no self-executing query objects;
- no ORM behavior, lazy loading, aggregate loading, identity maps, or implicit
  relation traversal;
- no arbitrary DSL call-site hover/navigation;
- no editor-side SQL rendering or editor-owned query semantics;
- no broad editor UX, completion, refactoring commands, or quick-fix program;
- no IntelliJ parity program that distracts from VS Code and core reliability.

Exit criteria:

- R17 corpus remains the evidence source for refactor safety, metadata
  freshness, Gradle behavior, schema drift, snapshots, and editor hardening;
- named R17 rename/delete/changing-column cases have baseline/fail/recover
  evidence;
- source-map-backed VS Code hover/copy SQL works for real generated fixed-read
  fixture calls and fails closed when metadata is stale;
- clean and incremental Gradle builds converge or fail deterministically across
  the fixture modules;
- domain and application modules remain Mortar-free;
- schema drift is documented and verified as a separate CLI/tooling workflow;
- no R16/R17 API decision is reversed or expanded;
- any new metadata, source-map, stale-detection, or compatibility contract has
  an accepted ADR before implementation;
- final verification evidence is recorded in this roadmap.

Planning debate outcome:

- R18 is a contract-hardening gate, not a general editor tooling release.
- `Refactor-safe` must distinguish compile-time Java failures, tooling
  freshness failures, and schema drift diagnostics.
- Rename/delete tests should use semantic baseline/fail/recover fixture cases,
  not brittle compiler-message or screenshot snapshots.
- Source-map-backed behavior is limited to generated fixed reads in R18.
- VS Code is primary because it exercises the shared LSP path; IntelliJ remains
  secondary.
- The processor `isolating` declaration with shared metadata output is an
  explicit R18 risk to verify.
- New ADRs are required only for cross-tool contract changes, not for slice
  sequencing.

R18.1 and R18.2 completion record:

- Added `docs/refactor-safety.md` as the canonical R18.1 contract defining
  Mortar refactor safety, supported R17-derived cases, deferred cases, stable
  diagnostic categories, baseline/fail/recover matrix shape, and forbidden
  brittle assertions.
- Added executable clean temp-compile matrix coverage in
  `java/processor/src/test/java/dev/mortar/processor/MortarRefactorSafetyMatrixTest.java`
  for annotated field rename/deletion, relation deletion, processor relation
  metadata diagnostics, generated metadata inventory, and changed
  column/relation metadata SQL inputs.
- Added R17 infrastructure rendered-SQL drift evidence in
  `examples/query-corpus-infrastructure-postgres/src/test/java/dev/mortar/examples/querycorpus/postgres/PostgresTicketReaderTest.java`
  showing that the baseline generated `TicketReader.findHeader` SQL matches
  the current snapshot expectation, while a changed `TicketRecord.summary`
  column-metadata variant no longer matches that stale expectation until the
  updated SQL expectation is asserted.
- Extended `rust/crates/mortar-compiler/src/lib.rs` schema-drift diagnostics
  to inspect relation local/target columns and added R17-shaped coverage for
  `tickets.customer_id`, `tickets.assigned_technician_id`,
  `tickets.status_code`, and `technicians.region`.
- The R18.1/R18.2 xhigh debate approved executable evidence now for
  deterministic clean compile and semantic SQL/schema cases, while keeping
  generated metadata freshness, source-map freshness, Gradle incremental
  convergence, and full schema-drift workflow claims in later R18 slices.
- The R18.2 unresolved-symbol cleanup xhigh debate concluded that javac
  unresolved symbols do not expose a stable portable category beyond
  `Diagnostic.Kind.ERROR` plus structured source evidence. The matrix now keeps
  structured diagnostics and asserts stale consumer source, stale symbol token,
  and independent regenerated `Q*` producer evidence instead of full messages,
  javac diagnostic codes, exact counts, ordering, or line/column numbers.
- R18.3 added ADR-0007 for the source-map/freshness contract. The accepted
  design keeps `META-INF/mortar/entities.json` as `mortar-metadata-v1` and adds
  `META-INF/mortar/source-map.json` with format `mortar-source-map-v1`, stable
  source anchors, generated fixed-read coordinates, ordered parameters, row
  types, snapshot keys, and semantic freshness fingerprints. It deliberately
  avoids absolute paths, timestamps, rendered SQL, line/column assertions, and
  editor behavior.
- R18.4 changed the processor incremental descriptor from `isolating` to
  `aggregating` because the processor emits shared metadata/source-map
  resources. It also uses non-claiming wildcard annotation support with a
  final-round shared-artifact write so shared artifacts are refreshed to empty
  inventories when annotated entities disappear, without starving unrelated
  processors or missing Mortar entities generated in later rounds.
- R18.4 added a temporary Gradle TestKit multi-module fixture proving
  domain/application sources remain Mortar-free, infrastructure annotated
  record changes regenerate `Q*`, `entities.json`, and `source-map.json`,
  source-map fingerprints change semantically, clean and incremental source-map
  inventories converge for the same source state, and deleted annotated
  entities do not leave stale valid metadata/source-map entries.
- Pre-R18.5 source-map contract hardening on 2026-06-02 added a shared
  Java-emitted `mortar-metadata-v1` and `mortar-source-map-v1` fixture covering
  columns, relation metadata, parameterized and zero-parameter fixed reads,
  generated-source coordinates, row types, snapshot keys, and semantic
  fingerprints. Java processor tests prove the checked-in fixture remains exact
  processor output; Rust compiler tests consume the same bytes and require
  source-map freshness validation to pass. This is contract evidence only, not
  VS Code/LSP editor UX.
- R18.5 implemented the strict layered hybrid approved by the xhigh debate:
  `mortar-source-map-v1` plus fresh `mortar-metadata-v1` is authoritative for
  generated fixed-read routing, while `mortar.sql.snap.json` remains the SQL
  evidence source. Explicit `mortar:snapshot` markers remain a legacy/manual
  path only and are not used as fallback when a generated fixed-read call has
  stale, missing, or ambiguous source-map data.
- R18.5 added Rust LSP definition support and source-map-backed hover, copy SQL,
  PostgreSQL EXPLAIN code actions, snapshot-entry navigation, and stale
  source-map diagnostics for canonical generated read-facade calls such as
  `QClient.CLIENT.read(renderer).findById(id)` and
  `QClient.CLIENT.read(renderer).findAll()`. The resolver selects entries by
  generated metamodel/read namespace context plus member, not by
  `generated_member` alone. The implementation does not add Java public API,
  does not render SQL in editor metadata, and does not navigate to generated
  Java line/column positions.
- R18.5 updated the VS Code smoke fixture to include
  `META-INF/mortar/entities.json`, `META-INF/mortar/source-map.json`, and a
  matching `mortar.sql.snap.json`, plus smoke assertions for hover, code
  actions, and definition through the shared LSP.
- R18.6 closed the remaining documentation gaps in `docs/lsp.md`,
  `docs/metadata.md`, `docs/refactor-safety.md`, and `docs/plan.md`. The R19
  handoff identifies Java call-resolution and editor-semantics hardening as
  the next risk to close before performance work or public demo claims. R19
  does not start in this slice.
- R18.5 focused verification passed on 2026-06-02:
  `cd rust && cargo test -p mortar-lsp`; from `editors/vscode`,
  `bun run typecheck` and `bun run test` with five VS Code smoke tests passing
  and the PostgreSQL EXPLAIN smoke skipped because no connection string was
  configured.
- R18.3/R18.4 focused verification passed on 2026-06-02:
  `gradlew.bat :java:processor:test --no-daemon`; and from `rust`,
  `cargo test -p mortar-compiler`.
- R18.3/R18.4 final verification passed on 2026-06-02:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`;
  `git diff --check`; broad private-residue scrub was reviewed and only
  pre-existing legal/standards/test-data references appeared outside
  build/cache outputs; diff-level private path/project scrub had zero matches.
- Focused verification passed on 2026-06-02:
  `gradlew.bat :java:processor:test --tests
  dev.mortar.processor.MortarRefactorSafetyMatrixTest --no-daemon`;
  `gradlew.bat :examples:query-corpus-infrastructure-postgres:test --tests
  dev.mortar.examples.querycorpus.postgres.PostgresTicketReaderTest.changedColumnMetadataIsASemanticSqlSnapshotDriftCase --no-daemon`;
  and
  `cd rust && cargo test
  detects_r17_schema_drift_cases_from_ticket_fixture_metadata`.
- Final R18 verification passed on 2026-06-02:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, `cargo test`,
  and `cargo publish --dry-run -p mortar-compiler`; from `editors/vscode`,
  `bun run typecheck` and `bun run test`; `git diff --check`; and diff-level
  plus broad private path/private project scrub excluding build outputs, caches,
  dependency folders, and VS Code test downloads.
- Migration note: no public Java API or generated Java API changed. A new
  parser-level `mortar-source-map-v1` artifact was added by ADR-0007, and the
  processor incremental classification changed from `isolating` to
  `aggregating` to match shared output behavior. R18.5 adds Rust LSP/VS Code
  consumption of the existing source-map contract without changing Java public
  API. Existing `mortar-metadata-v1` files remain compatible.
- Release note: no release, tag, publication, merge, push, or private
  application migration is authorized by this slice.

### R19: Java Call Resolution And Editor Semantics Hardening

Status: Done

Goal: harden Mortar's Java call-site recovery for source-map-backed editor
features before R20 performance work or public demo claims.

Scope:

- preserve ADR-0007: fresh `mortar-source-map-v1` plus
  `mortar-metadata-v1` remains authoritative for generated fixed-read query
  identity and snapshot routing;
- replace or constrain the R18 token-walking call matcher with syntax-aware
  local Java resolution in the Rust LSP;
- keep R18 canonical calls working:
  `QClient.CLIENT.read(renderer).findById(id)`,
  `QClient.CLIENT.read(renderer).findAll()`, and
  static-imported `CLIENT.read(renderer).findById(id)`;
- broaden only to explicit same-file local alias patterns that can be resolved
  without Java type binding;
- fail closed for unsupported generated-looking calls, stale source maps,
  missing metadata, ambiguous aliases, shadowing, reassignment, helper-returned
  receivers, wildcard static imports, and arbitrary Java call chains;
- drive hover, copy SQL, PostgreSQL EXPLAIN code actions, definition, and
  diagnostics from one resolver result so editor semantics do not diverge;
- expand Rust resolver matrix tests and VS Code smoke fixtures for canonical,
  alias, stale, ambiguous, and unsupported cases;
- document unsupported patterns clearly before any public demo material.

R19 slices:

- R19.1: Call-resolution contract, ADR-0008, and public planning docs. Status:
  Done.
- R19.2: Local Java syntax resolution foundation with R18 canonical parity.
  Status: Done.
- R19.2a: Residual fail-closed hardening before alias success. Status: Done.
- R19.3: Canonical aliases, local variables, and explicit static import
  resolution. Status: Done.
- R19.4: Fail-closed diagnostics and VS Code smoke fixture expansion. Status:
  Done.
- R19.5: Editor semantics review and R20 performance handoff. Status: Done.

Non-goals:

- no runtime Java API changes unless a later implementation slice records a
  separate justification;
- no generated Java API expansion;
- no performance implementation or benchmark claim;
- no AI/agent-friendly phase;
- no full IDE replacement;
- no full Java semantic engine, classpath-aware type binding, helper-returned
  receiver support, arbitrary DSL hover/navigation, or editor-side SQL
  rendering;
- no public release, release candidate, tag, publication, migration, or public
  demo claim.

R19 planning debate outcome:

- The required xhigh debate recommended a bounded hybrid: keep
  `mortar-source-map-v1` plus fresh `mortar-metadata-v1` authoritative, move
  Java call recovery toward local syntax-aware resolution, and broaden only to
  explicit same-file alias patterns.
- Continuing to broaden the R18 lightweight matcher was rejected because alias,
  scope, shadowing, and reassignment handling would become an ad hoc parser.
- Full `javac`, Eclipse JDT, or JavaParser symbol-solving integration was
  rejected for R19 because it would require classpath and workspace modeling
  larger than the hardening goal.
- Lexical alias heuristics without parser structure were rejected because they
  would be more likely to misroute SQL than to provide trustworthy editor
  output.
- ADR-0008 records the R19 boundary and complements ADR-0007 without replacing
  the source-map/freshness contract.

Exit criteria:

- R18 canonical generated fixed-read calls retain hover, copy SQL, EXPLAIN,
  definition, and fail-closed diagnostics behavior;
- supported local alias shapes are listed and covered by resolver tests;
- unsupported helper-returned receivers, field aliases, reassignment,
  conditional aliases, wildcard static imports, ambiguous imports, and
  generated-looking arbitrary calls fail closed;
- VS Code smoke fixtures cover canonical success, alias success, stale or
  missing source-map diagnostics, unsupported generated-looking calls, and
  snapshot definition routing;
- public docs avoid saying R19 implements a full Java parser or full Java call
  semantics;
- no Java runtime API, generated Java API, performance, release, or migration
  claim is added.

R19.1/R19.2 completion record:

- R19.1 added executable Rust LSP contract coverage for the R19 boundary:
  R18 canonical direct calls, explicit static imports, `findAll`,
  metamodel-context disambiguation, stale/missing/duplicate source-map
  fail-closed behavior, UTF-16 LSP positions, helper-returned receivers,
  wildcard static imports, local metamodel aliases, local read-namespace
  aliases, parenthesized aliases, reassigned aliases, same-name aliases in
  unrelated methods and lexical scopes, ordinary non-Mortar `read*` APIs,
  ambiguous bare receivers, harmless parentheses around canonical
  receivers/read namespaces, and generated-looking text in strings/comments.
- R19.2 added `tree-sitter-java` and `tree-sitter` to the Rust LSP tooling
  path and replaced token-only generated-call detection with local Java syntax
  parsing over the active document. The syntax resolver extracts import
  declarations, method invocations, immediate `.read(renderer)` receiver
  chains, and byte ranges, then returns the same `Call`, `FailClosed`, or
  `NotGeneratedCall` outcome used by hover, copy SQL, EXPLAIN, definition, and
  diagnostics.
- Source-map freshness remains authoritative: the syntax resolver only recovers
  generated metamodel/read-member context. Fresh `mortar-metadata-v1` plus
  `mortar-source-map-v1` still selects the snapshot key, and
  `mortar.sql.snap.json` remains the SQL evidence source.
- R19.2 deliberately did not add alias/local-variable success, helper-returned
  receiver support, wildcard static import support, Java type binding,
  classpath-aware semantics, Java public API changes, generated Java API
  changes, VS Code client expansion, performance work, release work, or R20
  behavior.
- Focused verification passed on 2026-06-02:
  `cd rust && cargo test -p mortar-lsp`.

R19.2a residual hardening completion record:

- The required xhigh architecture debate concluded that this slice should add
  hardening tests and reason-accurate fail-closed diagnostics only. Alias
  success remains deferred because the current generated-like local-name model
  does not carry enough metamodel/read-namespace identity for safe snapshot
  routing.
- `rust/crates/mortar-lsp` now fails closed when tree-sitter can still see a
  generated-looking call inside incomplete Java syntax. Hover, copy SQL,
  PostgreSQL EXPLAIN, and definition return no result, and diagnostics report
  incomplete Java syntax rather than stale source-map evidence.
- Canonical generated calls now unwrap nested parentheses and casts around the
  generated receiver or read namespace before source-map-backed snapshot
  routing. Source-map freshness and `mortar.sql.snap.json` remain authoritative.
- Local alias and local read-namespace alias success remains unsupported.
  Unsupported generated-looking alias calls, including lambda, catch, and
  switch block variants, fail closed with an unsupported-syntax diagnostic.
- No Java runtime API, generated Java API, VS Code client code,
  helper-returned receiver, wildcard static import, classpath/type binding,
  performance, release, or migration behavior changed. The only editor-visible
  behavior change is narrower fail-closed diagnostics for unsupported and
  incomplete generated-looking Java syntax.
- Verification passed on 2026-06-02: `gradlew.bat check --no-daemon`; from
  `rust`, `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`;
  `git diff --check`; and diff-level private path/project scrub with zero
  matches. Broad scrub excluding build, cache, dependency, and generated
  outputs found only pre-existing Java test fixture data outside this slice.
- Reviewer gates for ADR-0008 boundaries, source-map freshness authority,
  fail-closed behavior, public documentation scope, Java runtime/API stability,
  and private-path hygiene found no remaining blockers after documentation
  wording was narrowed to distinguish client code from client-visible
  diagnostics and full-document change recovery from incremental text sync.

R19.3/R19.4 completion record:

- The required xhigh architecture debate concluded on 2026-06-02 that R19.3
  should support only direct identity-bearing local bindings:
  `var client = QClient.CLIENT; client.read(renderer).findById(id)` and
  `var read = QClient.CLIENT.read(renderer); read.findById(id)`. Existing
  direct canonical calls and explicit static-import constants remain supported.
- `rust/crates/mortar-lsp` now resolves those two local alias shapes through
  the same source-map-backed path used by hover, copy SQL, PostgreSQL EXPLAIN,
  and snapshot-entry definition. Fresh `mortar-metadata-v1` plus
  `mortar-source-map-v1` remains authoritative, and valid alias syntax with
  stale or missing source-map evidence fails closed without marker fallback.
- Unsupported alias shapes remain unsupported: alias chains, type-only aliases,
  helper-returned receivers, parenthesized alias initializers, reassigned
  aliases, ambiguous conditional aliases, lambda/catch/switch alias scopes,
  field aliases, ambiguous explicit import collisions, wildcard static imports,
  cross-method or cross-file aliases, and aliases whose identity cannot be
  proven locally from same-file syntax.
- R19.4 added reason-specific fail-closed diagnostic codes:
  `mortar-alias-unsupported`, `mortar-alias-ambiguous`,
  `mortar-alias-reassigned`, `mortar-source-map-stale`,
  `mortar-snapshot-missing`, and `mortar-java-buffer-malformed`.
- VS Code smoke fixtures now cover canonical generated reads, supported local
  metamodel aliases, supported read-namespace aliases, unsupported alias
  diagnostics, copy SQL, EXPLAIN, and snapshot definition routing. The VS Code
  client remains thin; no client-side SQL routing or Java semantics were added.
- Changed modules/docs: `rust/crates/mortar-lsp`, `editors/vscode` smoke tests,
  `examples/editor-smoke/vscode`, `docs/lsp.md`, `docs/plan.md`, and this
  roadmap.
- Migration note: no Java runtime API, generated Java API, metadata format,
  source-map format, VS Code command contract, performance behavior, release,
  publication, migration, or R20 behavior changed.
- Focused verification passed on 2026-06-02: from `rust`,
  `cargo test -p mortar-lsp`; from `editors/vscode`, `bun run typecheck` and
  `bun run test`, with the PostgreSQL EXPLAIN datasource smoke pending because
  no `MORTAR_VSCODE_EXPLAIN_CONNECTION` was configured.

R20 handoff:

- R20 remains `Performance And Runtime Efficiency`.
- R20 starts after R19 because editor call-resolution semantics are now
  trustworthy enough that performance artifacts will not be paired with
  misleading editor claims.
- Java/Rust measurement boundaries stay explicit: Java owns runtime query-path
  benchmarking through JMH/PostgreSQL evidence, while Rust owns tooling and LSP
  resolver performance measurement.
- Benchmark reproducibility is the first R20 deliverable: retain repeated
  clean-commit artifacts before any public performance claim.
- Allocation and latency profiling must cover generated fixed reads, DSL reads,
  JDBC execution paths, and generated query path overhead against ordinary and
  tuned JDBC baselines.
- LSP resolver performance work should measure parser/resolver latency,
  allocation behavior, cache behavior, and large-document behavior without
  changing R19 semantics.
- No public performance claim is allowed without retained evidence and reviewer
  sign-off.

R19.5 completion record:

- The required xhigh architecture review concluded on 2026-06-02 that hover,
  copy SQL, PostgreSQL EXPLAIN, definition, and diagnostics share the same
  source-map-backed resolver outcome for generated fixed-read calls.
- Supported alias shapes remain exactly the two R19.3 same-file local binding
  forms. Unsupported generated-looking alias shapes fail closed with
  reason-specific diagnostics. Stale or missing generated source-map evidence
  cannot fall back to legacy snapshot markers.
- No resolver semantics gap was found, so no new semantic test was added.
  Existing Rust LSP tests and VS Code smoke tests already cover canonical
  success, supported alias parity across editor features, unsupported alias
  diagnostics, stale or missing source-map fail-closed behavior, and marker
  non-fallback for generated calls.
- R19.5 fixed closure hygiene only: `docs/metadata.md` now describes
  reason-specific fail-closed diagnostics, and the Rust LSP test fixture no
  longer contains a username-bearing synthetic URI.
- Changed modules/docs: `rust/crates/mortar-lsp`, `docs/lsp.md`,
  `docs/metadata.md`, `docs/plan.md`, and this roadmap.
- Migration note: no Java runtime API, generated Java API, metadata format,
  source-map format, VS Code command contract, performance behavior, release,
  publication, migration, or R20 implementation changed.
- Verification passed on 2026-06-02: `gradlew.bat check --no-daemon`; from
  `rust`, `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck` and `bun run test`;
  `git diff --check`; and private path/project scrub excluding build, cache,
  dependency, and generated outputs. `bun run test` passed with six VS Code
  smoke tests and one pending datasource-backed EXPLAIN smoke because
  `MORTAR_VSCODE_EXPLAIN_CONNECTION` was not configured.

### R20: Performance And Runtime Efficiency

Status: Done

Goal: produce evidence-backed performance and runtime-efficiency work after the
R19 editor semantics risk is closed.

Scope:

- repeated clean-commit JMH/PostgreSQL benchmark artifacts before any
  performance claim;
- allocation and latency evidence for generated fixed reads, DSL reads, and
  JDBC execution paths;
- PgJDBC prepared-statement, binary-transfer, and plan-cache benchmark
  variables where justified;
- Windows and Linux CI confidence for benchmark and verification gates;
- performance-report updates that distinguish measured evidence from product
  claims;
- no public release, release candidate, tag, publication, migration, or public
  performance claim without a separate go/no-go review.

Slices:

- R20.1: Benchmark readiness audit and source-backed research. Status: Done.
- R20.2: Canonical performance plan and public-claim policy. Status: Done.
- R20.3: Java runtime JMH/PostgreSQL baseline matrix with retained artifacts.
  Status: Done.
- R20.4: Generated fixed-read overhead and allocation profiling. Status: Done.
- R20.5: DSL query render/execute overhead profiling for broader read and write
  shapes. Status: Done.
- R20.6: Rust LSP resolver latency and allocation benchmark plan/harness.
  Status: Done.
- R20.7: Optimization candidates ranked only by retained evidence. Status:
  Done as a no-optimization decision gate.
- R20.8: Public performance report gate and reviewer sign-off. Status: Done as
  a public-report no-go.

R20.1/R20.2 completion record:

- The benchmark readiness audit is recorded in
  `docs/benchmarks/r20-benchmark-readiness.md`.
- Current benchmark inventory was classified as internal-only, harness-trustworthy,
  or not public-ready. Existing local JSON/build-output reports remain
  engineering evidence only, not public performance proof.
- Comparison rules now require exact baseline naming for ordinary JDBC, tuned
  PgJDBC, reusable prepared JDBC, maximum handwritten JDBC, jOOQ, and QueryDSL
  SQL. Controlled JDBC-double benchmarks are explicitly blocked from supporting
  real database claims.
- Required environment metadata, repeatability rules, retained artifact policy,
  and public claim policy are documented before any R20 optimization work.
- The xhigh performance debate concluded that Mortar should target near-zero
  overhead relative to tuned/reusable JDBC, not an unqualified "faster than
  JDBC" claim. Rust remains tooling/LSP infrastructure and is not part of the
  Java per-query runtime path.
- R20.1/R20.2 changed docs only: `docs/benchmarks/r20-benchmark-readiness.md`,
  `docs/benchmarks/README.md`, `docs/performance.md`, `docs/plan.md`, and this
  roadmap.
- Migration note: no Java public API, runtime behavior, generated Java API,
  Rust LSP semantics, metadata format, source-map format, benchmark harness
  code, release, publication, migration, or public performance claim changed.
- Research references added for JMH, JVM profiling, PgJDBC server prepare,
  PostgreSQL prepared-plan behavior, HikariCP statement-cache guidance,
  jOOQ/QueryDSL comparison risks, Criterion, tree-sitter, rust-analyzer
  performance practice, and public benchmark reporting discipline.

R20.3 completion record:

- The required xhigh benchmark debate concluded that R20.3 should be an
  artifact-readiness slice, not an optimization slice. The retained bundle is
  the evidence unit; terminal output and unbundled build-directory JSON remain
  insufficient for public claims.
- `.github/workflows/benchmarks.yml` now defaults to the canonical R20.3
  fixed-read include regex, requires `repeatCount >= 2`, runs throughput,
  allocation, and latency profiles, and uploads
  `java/benchmarks/build/reports/jmh/r20.3/**` with 90 day retention.
- The retained bundle contains raw JMH JSON in `results/`, plus
  `manifest.json`, `commands.txt`, `summary.md`, `review-notes.md`, and
  environment files. `docs/benchmarks/r20-postgres-baseline-manifest-template.json`
  records the same manifest shape for explicitly retained local bundles.
- The R20.3 matrix rows are ordinary JDBC, reusable prepared JDBC, ordinary JDBC
  `findById`, reusable prepared JDBC `findById`, tuned PgJDBC reusable JDBC,
  Mortar render-per-call, Mortar pre-rendered SQL, Mortar processor-generated
  executor, Mortar prepared processor-generated executor, Mortar tuned
  processor-generated executor, jOOQ reference, and QueryDSL SQL reference.
- Optional variants, handwritten generated-style Mortar rows, join/page rows,
  update-batch rows, and controlled fake-JDBC rows are excluded from R20.3
  interpretation and remain scoped to R20.4 or R20.5.
- Changed files/docs: `.github/workflows/benchmarks.yml`, `build.gradle.kts`,
  `java/benchmarks/src/test/java/dev/mortar/benchmarks/PostgresExecutionBenchmarkTest.java`,
  `docs/benchmarks/r20-postgres-baseline-manifest-template.json`,
  `docs/benchmarks/r20-benchmark-readiness.md`,
  `docs/benchmarks/README.md`, `docs/performance.md`, `docs/plan.md`, and this
  roadmap.
- Migration note: no Java public API, runtime behavior, benchmark threshold,
  generated Java API, Rust tooling behavior, release, publication, migration,
  optimization, or public performance claim changed.
- Verification passed on 2026-06-02:
  `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`,
  `gradlew.bat verifyBenchmarkWorkflow --no-daemon`, a focused live PostgreSQL
  JMH smoke that generated
  `java/benchmarks/build/reports/jmh/r20.3-smoke.json` as build output,
  `gradlew.bat check --no-daemon`, `cd rust && cargo fmt --all --check`,
  `cd rust && cargo clippy --all-targets --all-features -- -D warnings`,
  `cd rust && cargo test`, `cd editors/vscode && bun run typecheck`,
  `git diff --check`, and private path/project scrub excluding build, cache,
  dependency, and generated outputs.

R20.4 completion record:

- The required xhigh generated fixed-read debate concluded that R20.4 should be
  a narrow Java runtime evidence slice, not an optimization slice and not a
  docs-only slice.
- The canonical R20.4 scope is exactly six live PostgreSQL/Testcontainers rows:
  ordinary JDBC `findById`, reusable prepared JDBC `findById`, tuned PgJDBC
  reusable JDBC `findById`, Mortar processor-generated `findById`, Mortar
  prepared processor-generated `findById`, and Mortar tuned
  processor-generated `findById`.
- `java/benchmarks/build.gradle.kts` now provides
  `jmhR20GeneratedFixedRead`, `jmhR20GeneratedFixedReadAllocation`, and
  `jmhR20GeneratedFixedReadLatency` presets. The canonical include regex is
  `PostgresExecutionBenchmark\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$`.
- `PostgresExecutionBenchmarkTest` guards the R20.4 matrix names and excludes
  optional variants, handwritten generated-style Mortar rows, join/page rows,
  update-batch rows, jOOQ, QueryDSL SQL, and controlled fake-JDBC rows from the
  R20.4 interpretation boundary.
- Local smoke JSON under `java/benchmarks/build` is internal build output only.
  R20.4 does not create public performance evidence; repeated retained raw
  artifacts, manifest, commands, environment metadata, dataset notes,
  limitations, and review notes are still required before optimization
  proposals or public reporting.
- R20.4 `Done` means the generated fixed-read profiling harness, grouping
  guard, local instructions, and local smoke proof are complete. It does not
  mean the retained evidence gate for optimization proposals or public
  performance reporting is satisfied.
- Changed files/docs: `java/benchmarks/build.gradle.kts`,
  `java/benchmarks/src/test/java/dev/mortar/benchmarks/PostgresExecutionBenchmarkTest.java`,
  `docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
  `docs/performance.md`, `docs/plan.md`, and this roadmap.
- Migration note: no Java public API, runtime behavior, generated Java API,
  Rust tooling behavior, benchmark threshold, release, publication, migration,
  optimization, or public performance claim changed.
- Verification passed on 2026-06-02:
  `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`,
  a focused R20.4 live PostgreSQL JMH smoke that generated
  `java/benchmarks/build/reports/jmh/r20.4-generated-fixed-read-smoke.json` as
  build output, `gradlew.bat check --no-daemon`,
  `cd rust && cargo fmt --all --check`,
  `cd rust && cargo clippy --all-targets --all-features -- -D warnings`,
  `cd rust && cargo test`, `cd editors/vscode && bun run typecheck`,
  `git diff --check`, and private path/project scrub excluding build, cache,
  dependency, and generated outputs.

R20.5 completion record:

- The required xhigh DSL render/execute debate concluded that R20.5 should
  stay a narrow live PostgreSQL/Testcontainers measurement slice over existing
  broader DSL shapes, not an optimization, API, or library-ranking slice.
- The canonical R20.5 scope is exactly seven live PostgreSQL/Testcontainers
  rows: ordinary JDBC simple read, Mortar DSL render-per-call simple read,
  Mortar pre-rendered simple read, reusable prepared JDBC join/page, Mortar DSL
  join/page, reusable prepared JDBC update batch, and Mortar DSL update batch.
- `java/benchmarks/build.gradle.kts` now provides `jmhR20DslShapes`,
  `jmhR20DslShapesAllocation`, and `jmhR20DslShapesLatency` presets. The
  canonical include regex is
  `PostgresExecutionBenchmark\.(plainJdbcFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|plainJdbcJoinPageFetch|mortarJoinPageFetch|plainJdbcUpdateBatch|mortarUpdateBatch)$`.
- `PostgresExecutionBenchmarkTest` guards the R20.5 matrix names and excludes
  generated fixed-read R20.4 rows, optional variants, handwritten
  generated-style Mortar rows, jOOQ, QueryDSL SQL, and controlled fake-JDBC
  rows from the R20.5 interpretation boundary.
- Local smoke JSON under `java/benchmarks/build` is internal build output only.
  R20.5 does not create public performance evidence; repeated retained raw
  artifacts, manifest, commands, environment metadata, dataset notes,
  limitations, and review notes are still required before optimization
  proposals or public reporting.
- R20.5 `Done` means the DSL profiling harness, grouping guard, local
  instructions, and local smoke proof are complete. It does not mean the
  retained evidence gate for optimization proposals or public performance
  reporting is satisfied.
- Changed files/docs: `java/benchmarks/build.gradle.kts`,
  `java/benchmarks/src/test/java/dev/mortar/benchmarks/PostgresExecutionBenchmarkTest.java`,
  `docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
  `docs/performance.md`, `docs/plan.md`, and this roadmap.
- Migration note: no Java public API, runtime behavior, generated Java API,
  Rust tooling behavior, benchmark threshold, release, publication, migration,
  optimization, or public performance claim changed.
- Verification passed on 2026-06-02:
  `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`,
  a focused R20.5 live PostgreSQL JMH smoke that generated
  `java/benchmarks/build/reports/jmh/r20.5-dsl-shapes-smoke.json` as build
  output, `gradlew.bat check --no-daemon`,
  `cd rust && cargo fmt --all --check`,
  `cd rust && cargo clippy --all-targets --all-features -- -D warnings`,
  `cd rust && cargo test`, `cd editors/vscode && bun run typecheck`,
  `git diff --check`, and private path/project scrub excluding build, cache,
  dependency, and generated outputs.

R20.6 completion record:

- The required xhigh Rust LSP resolver debate concluded that R20.6 should
  measure current tooling/editor behavior only: tree-sitter parser latency,
  source-map-backed hover, copy SQL, PostgreSQL EXPLAIN code actions,
  definition, diagnostics, metadata/source-map/snapshot reads, large-document
  scans, and current full-buffer change behavior.
- `rust/crates/mortar-lsp` now provides the Criterion bench
  `r20_lsp_resolver`. The compatible harness choice is Criterion `0.7.0`
  because Mortar declares Rust `1.85` and Criterion `0.8.2` requires Rust
  `1.86`.
- Canonical R20.6 scenarios cover canonical generated reads, supported local
  metamodel aliases, supported read-namespace aliases, unsupported alias
  fail-closed diagnostics, stale source-map fail-closed behavior, missing
  snapshot fail-closed behavior, malformed-buffer diagnostics, deterministic
  large Java document scans, and success-to-failure-to-recovery full-buffer edit
  scripts.
- R20.6 keeps Rust tooling metrics separate from Java JMH/PostgreSQL runtime
  metrics. Criterion output under `rust/target/criterion` is local internal
  tooling evidence only, must not be committed, and cannot support JDBC,
  database, Java runtime, or public product performance claims.
- Allocation interpretation and R20.7 optimization candidates remain blocked
  until retained repeated artifacts, exact commands, environment metadata,
  corpus notes, limitations, profiler/allocation evidence, and review notes
  identify a dominant tooling cost.
- Changed files/docs: `rust/Cargo.toml`, `rust/Cargo.lock`,
  `rust/crates/mortar-lsp/Cargo.toml`,
  `rust/crates/mortar-lsp/src/lib.rs`,
  `rust/crates/mortar-lsp/benches/r20_lsp_resolver.rs`,
  `rust/crates/mortar-lsp/tests/r20_lsp_benchmark_harness.rs`,
  `rust/crates/mortar-lsp/tests/support/r20_lsp_benchmark.rs`,
  `docs/benchmarks/r20-benchmark-readiness.md`,
  `docs/benchmarks/README.md`, `docs/performance.md`, `docs/plan.md`, and this
  roadmap.
- Migration note: no Java public API, runtime behavior, generated Java API,
  metadata format, source-map format, VS Code command contract, R19 editor
  semantics, runtime optimization, release, publication, migration, or public
  performance claim changed.
- Verification passed on 2026-06-03:
  `cd rust && cargo fmt --all --check`,
  `cd rust && cargo clippy --all-targets --all-features -- -D warnings`,
  `cd rust && cargo test`, a focused R20.6 Criterion smoke that generated local
  `rust/target/criterion` output as uncommitted build output,
  `gradlew.bat check --no-daemon`, `cd editors/vscode && bun run typecheck`,
  `git diff --check`, and private path/project scrub excluding build, cache,
  dependency, generated, and target outputs.

R20.7/R20.8 completion record:

- The required xhigh optimization and public-report debate concluded that
  R20.3-R20.6 prove harness coverage, scenario coverage, and boundary-correct
  measurement surfaces, but not retained evidence strong enough to rank or
  implement an optimization candidate.
- `docs/benchmarks/r20-performance-gate.md` records the evidence-ranked
  optimization table. Java runtime candidates are separated from Rust tooling
  candidates, and every candidate is blocked until retained repeated artifacts,
  exact commands, clean commit metadata, environment metadata, dataset or corpus
  notes, limitations, profiler/allocation evidence where needed, derived
  summary, and reviewer notes identify a dominant cost.
- Blocked Java runtime candidates include generated binder/mapper tightening,
  prepared generated-query lifecycle changes, renderer reuse or pre-render
  caching, PgJDBC default/tuning changes, and benchmark threshold tightening.
- Blocked Rust tooling candidates include parser construction reuse or parser
  caching, source-map/snapshot lookup caching, diagnostics full-buffer scan
  optimization, and incremental parse or partial-sync strategy.
- R20.8 is a public-report no-go. The existing
  `docs/benchmarks/performance-report-2026-06-01.md` remains an internal
  public-readiness draft because it cites local build-output JSON, has pending
  release commit metadata, does not attach retained workflow artifacts, does
  not record independent reviewer sign-off against retained artifacts, and does
  not include broader workload evidence for application-level claims.
- Public wording is limited to internal/local evidence only until retained
  artifacts exist for the exact claim. Broad "Mortar is faster than JDBC"
  wording remains invalid, Java runtime and Rust tooling metrics remain
  separate, and controlled fake-JDBC rows remain excluded from database,
  PostgreSQL, driver, or product performance claims.
- R20 is Done as a measurement and decision-gate phase. It does not authorize a
  public performance report, runtime optimization, benchmark threshold change,
  Java API change, generated Java API change, R19 editor semantic change,
  release, publication, migration, tag, PR, push, or merge.
- Changed docs: `docs/benchmarks/r20-performance-gate.md`,
  `docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
  `docs/performance.md`, `docs/plan.md`, and this roadmap.
- R21 AI/agent-friendly work was deferred by R20 and is completed below. R22
  scalar/mutation contract work, R23 retained performance evidence and
  optimization, and R24 pre-release readiness remain deferred.

### R21: AI-Friendly Authoring And Query Recipes

Status: Done

Goal: Make Mortar easier for Java developers and AI coding agents to use
correctly on the first try without expanding the public API, changing runtime
behavior, making performance claims, or starting release/migration work.

Planned scope:

- canonical AI-friendly query authoring and recipe guide;
- generated `findById` and `findAll` recipes backed by compiling examples;
- explicit `.named(...)` and testkit SQL assertion recipes;
- Spring repository adapter and Clean Architecture placement guidance;
- AI-agent invariants for generated fixed reads, raw SQL avoidance, SQL
  visibility, source-map/snapshot contracts, self-executing query rejection,
  and performance-claim restraint;
- concise troubleshooting and comparison updates.

Non-goals:

- runtime optimization or public benchmark claims;
- broad DSL redesign;
- generated repositories, ORM behavior, generated writes, optional-filter
  method matrices, generated joins, `count`, or `exists`;
- app migration, release, tag, publish, PR, push, or announcement work.

Evidence:

- Required xhigh architecture debate completed on 2026-06-03. Outcome: R21
  should be a narrow docs-and-existing-examples convergence slice. Existing
  `examples/spring-boot-postgres` and `examples/clean-architecture-postgres`
  fixtures are enough compile-backed evidence; adding a duplicate fixture would
  increase drift risk.
- `docs/query-recipes.md` is the canonical R21 authoring guide and is linked
  from `README.md`, `docs/getting-started.md`, `docs/usage-guide.md`,
  `docs/spring-boot-postgres-example.md`,
  `docs/ddd-clean-architecture-example.md`,
  `docs/examples/spring-clean-architecture-repository.md`,
  `docs/comparison.md`, and `docs/troubleshooting.md`.
- The guide documents generated `findById`, generated `findAll`, explicit
  `.named(...)`, testkit SQL assertions, Spring repository adapters, Clean
  Architecture placement, AI-agent invariants, common mistakes, troubleshooting,
  and Mortar/jOOQ/QueryDSL/JPA selection guidance.
- Changed docs: `docs/query-recipes.md`, `README.md`,
  `docs/getting-started.md`, `docs/usage-guide.md`,
  `docs/spring-boot-postgres-example.md`,
  `docs/ddd-clean-architecture-example.md`,
  `docs/examples/spring-clean-architecture-repository.md`,
  `docs/comparison.md`, `docs/troubleshooting.md`, `docs/plan.md`, and this
  roadmap.

Verification passed on 2026-06-03:

- `gradlew.bat :examples:spring-boot-postgres:test --no-daemon`;
- `gradlew.bat :examples:clean-architecture-postgres:check --no-daemon`;
- `gradlew.bat :java:testkit:test --no-daemon`;
- `gradlew.bat check --no-daemon`;
- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- changed-doc private path/project scrub excluding build, cache, dependency,
  generated, and target outputs.

Review result:

- No Java public API, generated API, runtime behavior, editor semantic,
  performance, benchmark threshold, release, publication, migration, tag, PR,
  push, or merge changed.
- No unsupported performance, replacement, release, or migration claim was
  introduced.
- Review follow-up: the R21 recipe authoring rules were corrected to keep
  deferred `count` and `exists` scalar-query contracts out of the current
  supported authoring path.
- Remaining risk: snippets are excerpts from existing compiling examples rather
  than a new standalone recipe module. This is intentional to avoid duplicate
  fixture drift.

### R22: Scalar And Mutation Contracts

Status: Done

Goal: Make Mortar cover the minimum real repository persistence cycle before
pre-release readiness is evaluated: read, count, check existence, create,
update, delete, and optionally batch writes where the existing API can support
them without surface explosion.

Planned scope:

- scalar query contracts for `count` and `exists`;
- mutation contracts for insert, update, delete, and bounded batch writes;
- visible SQL, parameters, parameter types, metadata, snapshots, and testkit
  assertions for scalar and mutation paths;
- JDBC execution contracts that preserve runtime boundaries;
- Spring repository and Clean Architecture examples for scalar and mutation
  repository methods;
- query recipe and troubleshooting updates;
- diagnostics for unsupported scalar/mutation shapes;
- ADR if public scalar or mutation contracts change architecture or API shape.

Non-goals:

- ORM behavior, dirty checking, managed entity state, lazy loading, aggregate
  graph loading, or implicit relation persistence;
- generated repositories or Spring Data-style method-name derivation;
- self-executing generated query or mutation objects;
- generated optional-filter matrices or generated write method explosion;
- raw SQL as the primary write path;
- runtime performance optimization or public benchmark claims;
- private application migration;
- release, tag, publish, PR, push, or announcement work.

Exit criteria:

- scalar and mutation API boundaries are documented and tested;
- existing mutation foundations are reviewed before new repository-facing API is
  added;
- Java compile/tests, PostgreSQL behavior, Rust tooling checks, VS Code
  typecheck, whitespace, scrub, and review gates pass;
- examples compile and keep Mortar inside infrastructure adapters;
- docs state that Mortar remains explicit persistence, not an ORM;
- no performance, release, migration, or replacement claims are added.

Evidence:

- Required xhigh architecture debate completed on 2026-06-03. Outcome: keep R22
  as one persistence-cycle gate, but split public contracts into row reads,
  scalar reads, and mutations. `count` and `exists` are DSL-only; no generated
  scalar or write facades were added.
- `java/core` adds `CountSpec`, `ExistsSpec`, `ScalarSpec<T>`,
  `MortarBoundScalar<T>`, `MortarBoundMutation`,
  `MortarReturningMutation<T>`, and `MutationResultMode`.
- `java/dialect-postgres` renders PostgreSQL `count`, `exists`, insert,
  update, delete, and `RETURNING` SQL with visible ordered parameters and
  metadata.
- `java/runtime-jdbc` executes scalar reads, row-count mutations, returning
  mutations, and same-SQL non-returning batches without adding execution
  methods to scalar or mutation values.
- `java/testkit` asserts bound scalar and mutation SQL contracts.
- Spring Boot and Clean Architecture examples include `count`, `exists`,
  create, update, and delete repository/adapter methods while keeping Mortar in
  infrastructure.
- `docs/adr/0009-r22-scalar-and-mutation-contracts.md` records the public
  contract decision.
- Processor metadata/source-map and Rust LSP contracts did not change because
  R22 deliberately avoided generated scalar/write facades.

Changed modules/docs:

- `java/core`, `java/dialect-postgres`, `java/runtime-jdbc`, `java/testkit`;
- `examples/spring-boot-postgres`;
- `examples/clean-architecture-postgres`;
- `rust/crates/mortar-cli`;
- `docs/adr/0009-r22-scalar-and-mutation-contracts.md`;
- `docs/adr/index.md`;
- `docs/api-reference.md`;
- `docs/query-recipes.md`;
- `docs/spec/architecture.md`;
- `docs/sql-snapshots.md`;
- `docs/testkit.md`;
- `docs/troubleshooting.md`;
- `docs/usage-guide.md`;
- `docs/plan.md` and this roadmap.

Verification passed on 2026-06-03:

- initial focused core gate failed before implementation because new scalar and
  bound mutation contracts did not exist;
- `gradlew.bat :java:core:test --no-daemon`;
- focused scalar/mutation gate:
  `gradlew.bat :java:dialect-postgres:test :java:runtime-jdbc:test :java:testkit:test :examples:spring-boot-postgres:test :examples:clean-architecture-postgres:test --no-daemon`;
- `gradlew.bat check --no-daemon`;
- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- private path/user/project scrub excluding build, cache, dependency,
  generated, and target outputs.

Migration note: R22 adds public scalar and bound mutation contracts. Existing
row-read, generated fixed-read, PostgreSQL mutation spec/rendering, JDBC
batch, processor metadata/source-map, Rust compiler, LSP, VS Code, IntelliJ,
and Spring wiring contracts remain compatible.

Release/performance note: R22 performs no release, tag, publish, PR, push,
announcement, private application migration, runtime optimization, benchmark
threshold change, public benchmark report, or performance claim.

### R23: Retained Performance Evidence And Optimization

Status: Planned

Goal: Finish the performance program properly before pre-release readiness is
evaluated. R20 created measurement discipline, benchmark harnesses, and public
claim policy. R23 must produce retained, repeated, reviewable evidence on the
post-R22 API surface, then optimize only if evidence identifies a dominant cost.

Planned scope:

- repeated Java runtime JMH/PostgreSQL benchmark workflows on a clean commit
  with retained raw artifacts;
- generated fixed-read, scalar, mutation, broader DSL, join/page, and batch
  scenarios where supported by R22;
- fair and explicitly bounded JDBC, PgJDBC-tuned JDBC, jOOQ, and QueryDSL
  reference comparisons;
- Rust LSP/tooling benchmarks kept separate from Java runtime evidence;
- commands, environment metadata, dataset/corpus notes, limitations, raw JSON,
  derived summaries, and reviewer notes;
- allocation and latency profiling before optimization proposals;
- evidence-ranked optimizations only, with before/after retained artifacts and
  review;
- public performance wording go/no-go for exact claims only.

Non-goals:

- optimizing from local smoke output;
- public performance claims without retained artifacts and reviewer sign-off;
- changing API shape only for benchmark convenience;
- mixing Rust tooling latency with Java/JDBC runtime claims;
- release, tag, publish, PR, push, migration, or announcement work.

Exit criteria:

- retained artifacts exist for the measured scenarios;
- benchmark-readiness review approves the evidence boundary;
- optimization decisions are ranked by retained evidence;
- any implemented optimization has before/after retained evidence;
- public performance claims remain blocked unless exact retained evidence and
  reviewer sign-off exist.

### R24: Pre-Release Readiness

Status: Planned

Goal: Decide whether Mortar is ready for a first public alpha after R22 closes
the repository persistence-cycle surface and R23 completes retained performance
evidence plus any justified optimization work.

Planned scope:

- public API and Javadocs review;
- README, getting started, recipes, troubleshooting, comparison, release policy,
  and changelog review;
- Maven Central and GitHub release dry-run verification;
- Rust crate dry-run verification where applicable;
- CI status and branch protection review;
- public docs scrub for private paths, usernames, local build-output claims,
  and unsupported performance statements;
- benchmark-report go/no-go only if retained evidence exists;
- explicit `0.1.0-alpha` go/no-go decision.

Non-goals:

- release, tag, publish, or announcement execution;
- private application migration;
- new product feature implementation;
- public performance report without retained benchmark evidence.

## Canonical Update Protocol

Every completed slice must update this roadmap in the same change.

Required update:

- Change slice status.
- Add verification evidence.
- Link changed modules/docs.
- Add or link ADR if the slice changed architecture.
- Add migration notes if public API changed.
- Add research references if the slice depends on new external evidence.

If implementation discovers that a slice is wrong, do not silently drift. Update the roadmap first or in the same pull request, marking the old slice `Replaced` and naming the replacement.

## Reference Research

- Spring Data JPA documents query methods, Query by Example, and Querydsl integration, validating that Spring users already expect repository-driven querying but still need stronger refactor safety for complex cases: https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html and https://docs.spring.io/spring-data/jpa/reference/repositories/query-by-example.html
- Spring Boot 3.5 documents `@AutoConfiguration`, conditional beans, `AutoConfiguration.imports`, and `ApplicationContextRunner` for custom auto-configuration testing, supporting Mortar's starter structure: https://docs.spring.io/spring-boot/3.5-SNAPSHOT/reference/features/developing-auto-configuration.html
- jOOQ validates code generation and type-safe SQL construction as a mature market direction: https://www.jooq.org/doc/latest/manual
- QueryDSL positions itself as compact, safe, unified queries for Java, validating generated query types as a usable developer model: https://querydsl.com/
- Ebean query beans validate IDE-friendly generated query objects for type-safe predicates: https://ebean.io/docs/query/background/typesafe
- SQLx validates compile-time checked query tooling and offline analysis in Rust: https://github.com/launchbadge/sqlx
- SQLDelight validates generated type-safe APIs from database query definitions: https://sqldelight.github.io/sqldelight/latest/
- IntelliJ Platform SDK supports inspections, annotations, and plugin distribution, validating the IDE tooling path: https://plugins.jetbrains.com/docs/intellij/inspections.html and https://plugins.jetbrains.com/docs/intellij/intellij-platform.html
- IntelliJ Platform Gradle Plugin 2.x is the official JetBrains build tooling for IntelliJ plugins, and its dependency DSL documents `intellijIdea("2026.1.2")`, `bundledPlugin("com.intellij.java")`, and `testFramework(TestFrameworkType.Platform)` for Java plugin development: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html and https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
- IntelliJ Platform Gradle Plugin publishing tasks publish the `buildPlugin` or signed archive to JetBrains Marketplace and accept token/channel settings through the `intellijPlatform.publishing` DSL, supporting Mortar's token-gated Marketplace packaging: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html and https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html
- LSP supports hover responses with markdown/code blocks, validating an editor-neutral future: https://ntaylormullen.github.io/language-server-protocol/specifications/specification-3-17/
- Official LSP 3.17 documents hover, code action, and go-to-definition as
  position/range-based language-feature requests that may return no result when
  a server cannot resolve valid data:
  https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
- VS Code's language-server guide documents language servers as separate
  processes because parsing and static analysis can be CPU and memory
  intensive, supporting R19's choice to keep Java call resolution in the shared
  LSP tooling path:
  https://code.visualstudio.com/api/language-extensions/language-server-extension-guide
- VS Code extension APIs document command registration/execution, which Mortar
  uses for smoke tests and the `mortar.copySql` / `mortar.explainSql` command
  bridge:
  https://code.visualstudio.com/api/references/vscode-api
- Tree-sitter parser guidance and the `tree-sitter-java` Rust crate validate a
  local syntax-tree option for R19 while making clear this is parser support,
  not Java type binding:
  https://tree-sitter.github.io/tree-sitter/using-parsers/ and
  https://docs.rs/tree-sitter-java/latest/tree_sitter_java/
- Java `JavaCompiler` and `Trees` document compiler/tooling APIs that can
  provide diagnostics and syntax-tree access, but using them for R19 would add
  Java process, classpath, and workspace integration cost:
  https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/JavaCompiler.html
  and
  https://docs.oracle.com/en/java/javase/21/docs/api/jdk.compiler/com/sun/source/util/Trees.html
- Eclipse JDT Language Server documents a full Java LSP built on Eclipse JDT
  with Gradle/Maven, diagnostics, completion, navigation, and annotation
  processing support, validating the semantic tooling option while showing why
  it is larger than R19:
  https://github.com/eclipse-jdtls/eclipse.jdt.ls
- Eclipse JDT `ASTParser` documents Java AST parsing and optional binding
  computation behavior, supporting R19's distinction between syntax parsing and
  full semantic binding:
  https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/dom/ASTParser.html
- JavaParser and JavaSymbolSolver validate AST plus symbol-solving approaches,
  but R19 defers that Java dependency and classpath complexity:
  https://github.com/javaparser/javaparser
- JMH is the standard Java harness for JVM microbenchmarks and should be used for performance claims: https://github.com/openjdk/jmh
- JMH samples document dead-code, blackhole, loop, fork, per-invocation setup,
  and profiler pitfalls that R20 benchmark plans must account for:
  https://github.com/openjdk/jmh/tree/master/jmh-samples/src/main/java/org/openjdk/jmh/samples
- PgJDBC server-prepared statements can reduce repeated SQL text, enable binary transfer, reuse execution plans, and reuse result metadata; `prepareThreshold`, `preparedStatementCacheQueries`, and `binaryTransfer` are relevant Mortar benchmark variables: https://pgjdbc.github.io/pgjdbc/documentation/server-prepare/ and https://jdbc.postgresql.org/documentation/use/?lang=en
- PostgreSQL's extended query protocol separates Parse, Bind, and Execute, and prepared statement/portal state can be reused across executions: https://www.postgresql.org/docs/17/protocol-flow.html
- PostgreSQL `plan_cache_mode` documents the custom-plan versus generic-plan tradeoff for prepared statements: https://www.postgresql.org/docs/17/runtime-config-query.html
- HikariCP documents pool-level statement caching as an anti-pattern and recommends relying on driver-level caches: https://sources.debian.org/src/hikaricp/2.7.1-2/README.md
- jOOQ documents statement type, statement reuse, and performance considerations
  that make reference-library comparisons sensitive to statement lifecycle and
  fetch behavior:
  https://www.jooq.org/doc/latest/manual/sql-building/dsl-context/custom-settings/settings-statement-type/,
  https://www.jooq.org/doc/latest/manual/sql-execution/reusing-statements/,
  and https://www.jooq.org/doc/latest/manual/sql-execution/performance-considerations/
- QueryDSL SQL documents SQL query execution APIs and rendered SQL/bindings,
  supporting R20's requirement to normalize SQL shape, binding behavior, and row
  materialization before comparison:
  https://querydsl.com/static/querydsl/latest/reference/html_single/ and
  https://querydsl.com/static/querydsl/5.0.0/apidocs/com/querydsl/sql/SQLQuery.html
- Criterion documents warmup, statistical analysis, profiling mode, custom
  measurements, and CI noise caveats for future Rust tooling benchmarks:
  https://bheisler.github.io/criterion.rs/book/index.html
- Tree-sitter parser docs and Rust APIs document incremental parsing,
  old-tree reuse, parse options, and query cursors for future LSP parser/resolver
  benchmarks:
  https://tree-sitter.github.io/tree-sitter/using-parsers/3-advanced-parsing.html,
  https://docs.rs/tree-sitter/latest/tree_sitter/struct.Parser.html,
  https://docs.rs/tree-sitter/latest/tree_sitter/struct.ParseOptions.html,
  and https://docs.rs/tree-sitter/latest/tree_sitter/struct.QueryCursor.html
- rust-analyzer documents batch and incremental analysis performance tooling,
  supporting R20's split between LSP batch/corpus and incremental-edit
  benchmark shapes:
  https://rust-analyzer.github.io/book/contributing/ and
  https://rust-analyzer.github.io/book/contributing/guide.html
- ACM artifact badging and SPEC run rules support R20's retained artifact,
  configuration disclosure, and independent reproducibility requirements:
  https://www.acm.org/publications/policies/artifact-review-and-badging-current
  and https://www.spec.org/cpu2026/docs/runrules.html
- Recent JVM benchmarking research warns that even JMH microbenchmarks can be misleading when JVM profile context is unrealistic, supporting Mortar's real-PostgreSQL benchmark matrix requirement: https://arxiv.org/abs/2605.23570
- Testcontainers for Java supports PostgreSQL integration tests with throwaway real database instances: https://java.testcontainers.org/modules/databases/postgres/
- PostgreSQL 16 array operators document array containment `@>` and overlap `&&`, supporting Mortar's array predicate rendering: https://www.postgresql.org/docs/16/functions-array.html
- PostgreSQL 16 JSON/JSONB operators document JSONB containment `@>`, supporting Mortar's JSONB predicate rendering: https://www.postgresql.org/docs/16/functions-json.html
- PostgreSQL 16 full-text search documents match operator `@@` and `websearch_to_tsquery`, supporting Mortar's full-text predicate rendering: https://www.postgresql.org/docs/16/functions-textsearch.html and https://www.postgresql.org/docs/16/textsearch-intro.html
- Empirical Java database-access research found SQL query, schema, and API bugs account for 84.2% of studied database access bugs, supporting Mortar's focus on query/schema/API safety: https://arxiv.org/abs/2405.15008
- 2026 research on static type checking for database access code reinforces the need to bridge Java types and database dictionaries during compilation: https://arxiv.org/abs/2605.02569
- Gradle incremental annotation processing distinguishes isolating and aggregating processors, requires processor opt-in metadata, and makes shared outputs an explicit convergence risk to prove before incremental claims: https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing
- Gradle incremental build guidance documents task inputs, outputs, and stale-output handling, supporting R18's separation of clean compile evidence from later incremental convergence evidence: https://docs.gradle.org/current/userguide/incremental_build.html
- Oracle `javac` annotation processing documents processor discovery, processing rounds, generated sources, and final compilation, supporting R18 clean temp-compile matrix tests: https://docs.oracle.com/en/java/javase/11/tools/javac.html
- Java annotation processing APIs provide `Messager`, `Filer`, and `Processor` contracts, supporting stable Mortar diagnostic-code assertions instead of full compiler-output snapshots: https://docs.oracle.com/en/java/javase/15/docs/api/java.compiler/javax/annotation/processing/package-summary.html
- Java `Diagnostic` documents stable kind/source/position accessors while diagnostic codes are implementation-dependent and messages are localized, supporting R18.2's refusal to assert javac unresolved-symbol codes or full message snapshots: https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/tools/Diagnostic.html
- Java `JavaCompiler` documents diagnostic collection for tool integrations, supporting Mortar's direct compile-test harness for generated-source contract tests: https://docs.oracle.com/en/java/javase/22/docs/api/java.compiler/javax/tools/JavaCompiler.html
- Google compile-testing validates compile-test patterns for javac and annotation processors, though R18.2 uses Mortar's existing direct `JavaCompiler` harness to avoid adding a new dependency: https://github.com/google/compile-testing
