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
- Java publishing metadata declares group `dev.mortar`, Apache-2.0 license, SCM, and developer metadata.
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

Goal: Make SQL transparency visible in Ricardo's primary editor first while keeping the implementation editor-neutral.

Ordering note: On 2026-05-31, Ricardo confirmed VS Code is his primary Java editor. Mortar therefore prioritizes LSP and VS Code before heavyweight IDE-specific plugins.

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
- Local release dry-run verification passed on 2026-06-01 with `gradlew.bat publishToMavenLocal --no-daemon` and `cargo publish --dry-run -p mortar-compiler --allow-dirty`. The Rust command used `--allow-dirty` locally because this workspace has uncommitted implementation changes; CI runs without that flag on a clean checkout.
- Rust verification passed on 2026-06-01 with `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test`.
- Java root verification passed on 2026-06-01 with `gradlew.bat check --no-daemon`.

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
- JMH is the standard Java harness for JVM microbenchmarks and should be used for performance claims: https://github.com/openjdk/jmh
- PgJDBC server-prepared statements can reduce repeated SQL text, enable binary transfer, reuse execution plans, and reuse result metadata; `prepareThreshold`, `preparedStatementCacheQueries`, and `binaryTransfer` are relevant Mortar benchmark variables: https://pgjdbc.github.io/pgjdbc/documentation/server-prepare/ and https://jdbc.postgresql.org/documentation/use/?lang=en
- PostgreSQL's extended query protocol separates Parse, Bind, and Execute, and prepared statement/portal state can be reused across executions: https://www.postgresql.org/docs/17/protocol-flow.html
- PostgreSQL `plan_cache_mode` documents the custom-plan versus generic-plan tradeoff for prepared statements: https://www.postgresql.org/docs/17/runtime-config-query.html
- HikariCP documents pool-level statement caching as an anti-pattern and recommends relying on driver-level caches: https://sources.debian.org/src/hikaricp/2.7.1-2/README.md
- Recent JVM benchmarking research warns that even JMH microbenchmarks can be misleading when JVM profile context is unrealistic, supporting Mortar's real-PostgreSQL benchmark matrix requirement: https://arxiv.org/abs/2605.23570
- Testcontainers for Java supports PostgreSQL integration tests with throwaway real database instances: https://java.testcontainers.org/modules/databases/postgres/
- PostgreSQL 16 array operators document array containment `@>` and overlap `&&`, supporting Mortar's array predicate rendering: https://www.postgresql.org/docs/16/functions-array.html
- PostgreSQL 16 JSON/JSONB operators document JSONB containment `@>`, supporting Mortar's JSONB predicate rendering: https://www.postgresql.org/docs/16/functions-json.html
- PostgreSQL 16 full-text search documents match operator `@@` and `websearch_to_tsquery`, supporting Mortar's full-text predicate rendering: https://www.postgresql.org/docs/16/functions-textsearch.html and https://www.postgresql.org/docs/16/textsearch-intro.html
- Empirical Java database-access research found SQL query, schema, and API bugs account for 84.2% of studied database access bugs, supporting Mortar's focus on query/schema/API safety: https://arxiv.org/abs/2405.15008
- 2026 research on static type checking for database access code reinforces the need to bridge Java types and database dictionaries during compilation: https://arxiv.org/abs/2605.02569
