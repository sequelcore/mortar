# Mortar Development Plan

Date: 2026-06-01
Status: Public planning archive

This document records the current public development plan for Mortar. The
canonical long-term status source is [`roadmap.md`](roadmap.md). When this file
and the roadmap disagree, the roadmap is authoritative.

## Objective

Build Mortar as a Java + Rust query system for Java-first, refactor-safe,
SQL-transparent persistence in Spring applications.

Mortar is not trying to hide SQL. It is designed so application developers can
write normal Java code while still seeing, testing, explaining, and benchmarking
the exact SQL that will run.

## Product Boundary

Mortar is scoped around:

- a Java-first query DSL;
- annotation-processor generated metamodels and common executors;
- PostgreSQL SQL rendering;
- predictable JDBC execution;
- Spring Boot integration;
- SQL snapshots, diagnostics, and editor visibility;
- Rust-based CLI, compiler, LSP, and reporting tooling.

Mortar is intentionally not a full ORM. It does not own aggregate loading,
hidden lazy loading, object identity tracking, or broad database abstraction
until the project has evidence for each added dialect.

## Architecture Plan

Java owns the user-facing application API:

- `java/core`: framework-free query model and DSL contracts;
- `java/processor`: generated metamodels, metadata, and generated executor code;
- `java/dialect-postgres`: PostgreSQL rendering;
- `java/runtime-jdbc`: JDBC execution of rendered/generated query plans;
- `java/spring-boot-starter`: Spring Boot adapter;
- `java/testkit`: SQL and EXPLAIN assertions.

Rust owns tooling and compiler infrastructure:

- `rust/crates/mortar-cli`: command-line tooling;
- `rust/crates/mortar-compiler`: metadata, snapshot, and report foundations;
- `rust/crates/mortar-lsp`: editor-neutral LSP server.

Rust is not part of the default per-query Spring/JDBC runtime path.

## Completed Public Foundation

The following work is complete and represented in the roadmap:

- project foundation, licensing, governance, and CI;
- core query model and Java-first DSL;
- PostgreSQL dialect;
- JDBC runtime;
- Spring Boot starter;
- testkit;
- Rust CLI/compiler tooling;
- SQL snapshots and static diagnostics;
- JMH performance program;
- LSP, VS Code, Neovim guidance, and IntelliJ adapter;
- documentation, examples, release policy, and benchmark reports.

## Post-R15 Roadmap Direction

R15 completed the initial public API readiness hardening path. The next work is
not an application migration. Mortar should first mature its Java authoring
surface, prove that surface against realistic public query fixtures, and harden
tooling before real projects depend on it.

The planned sequence is:

- R16: Java-First Ergonomics And Query Authoring.
- R17: Real-Query Coverage Gate.
- R18: Stability, Refactor Safety And Tooling Hardening.
- R19: Pre-Release Candidate Hardening.

Existing application migration waits until Mortar is much more mature. R17 must
prove R16 ergonomics against a realistic public fixture corpus before any such
migration is considered, and R19 is a go/no-go gate for future beta or controlled
migration pilot planning rather than a release promise.

## Verification Matrix

Java gate:

```bash
gradlew.bat check --no-daemon
```

Rust gate:

```bash
cd rust
cargo fmt --all --check
cargo clippy --all-targets --all-features -- -D warnings
cargo test
```

VS Code extension gate:

```bash
cd editors/vscode
bun run typecheck
bun run test
```

Benchmark report gate:

```bash
gradlew.bat :java:benchmarks:jmhPostgresExecution --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhPostgresExecutionLatency --no-daemon
```

Public documentation scrub:

- search public source files for local absolute paths, private workspace roots,
  hostnames, and usernames;
- exclude generated build outputs and dependency caches;
- treat any match as a release blocker until it is replaced with a relative
  path, neutral fixture, or documented placeholder.

## R16 Canonical Design: Java-First Ergonomics And Query Authoring

Status: Done

R16 is a design target for implementation slices, not an implementation record.
It must not be marked `In Progress` until product code exists, and it must not
be marked `Done` until implementation, tests, docs, and verification evidence
exist.

### Problem Statement

Mortar already gives Java developers a refactor-safe DSL, generated `Q*`
metamodels, generated `findById(renderer)` and `findAll(renderer)` executors,
visible SQL, SQL snapshots, JDBC execution, Spring Boot wiring, and editor
tooling. R16 exists because the current surface still leaves too much
application-query friction for everyday repository code:

- raw SQL strings can survive compilation and break at runtime after a Java
  field, DTO, table, or column changes;
- current Mortar DSL and generated executors are safer than raw SQL but can
  still feel verbose for common real queries because repositories must manage
  `MortarDb`, `QueryRenderer`, `QuerySpec`, generated parameter records, DTO
  mapping, and snapshot assertions separately;
- users need Java-first authoring that lets autocomplete lead them through
  entity fields, filters, projections, sorting, paging, and execution shape,
  instead of writing SQL text inside Java strings;
- SQL transparency remains non-negotiable: every generated or authored query
  must expose the SQL, parameters, metadata, and testkit/snapshot path before it
  is trusted.

R16 is not an application migration, release promise, ORM pivot, or hidden
repository abstraction. It is a query-authoring ergonomics pass over the public
Mortar surface.

### Target Query Shapes

R16 targets less friction than handwritten SQL plus JDBC binding and row mapping
for bounded read-query families. It does not claim Mortar is simpler than SQL
for every query. Fixed-shape single-table reads are the R16 implementation
target; broader query families are documented here so the design has an
end-to-end direction, but they remain gated by R17 or R18.

R16 implementation targets:

- `findById`: primary-key lookup with generated parameter binding, visible SQL,
  and at-most-one-row execution.
- `findAll`: explicit full-table read for small reference tables only, with SQL
  visible and diagnostics able to flag unbounded usage.
- Repository DTO mapping: repositories map generated fixed-read rows into
  application DTOs or domain-facing records after explicit
  `MortarJdbcClient` execution.
- Repository/service usage: examples must keep Mortar inside infrastructure
  adapters. Domain ports and services expose business methods and DTOs, not
  Mortar query types.

R17 corpus-gated targets:

- `findBy` with optional filters: no generated method explosion such as
  `findByNameAndStatusAndCreatedAt`; use a typed filter builder where absent
  values are skipped explicitly.
- Stable pagination: require deterministic `orderBy(...)` before page execution
  unless the query is explicitly marked unsafe or test-only.
- `exists`: project an existence boolean for a typed predicate without loading
  rows.
- `count`: count rows matching typed predicates without projection mapping.
- Simple join/read model: one or two explicit relation paths for read models
  only, with no implicit aggregate loading and no hidden lazy traversal.

Rejected for R16:

- generated update or batch facades;
- generated write repositories;
- query objects that execute themselves;
- generated aggregate loading or relation traversal.

### Implemented API Shape

The examples below distinguish the implemented R16 fixed-read surface from
deferred design targets.

Current R15 style for an application-specific query:

```java
public Optional<ClientSummary> findActiveById(long id) {
    List<ClientSummary> rows = jdbcClient.fetch(findActiveByIdQuery(id), ClientSummary.class);
    return rows.stream().findFirst();
}

QuerySpec findActiveByIdQuery(long id) {
    return db.from(CLIENT)
        .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
        .where(client -> client.id.eq(id))
        .where(client -> client.active.eq(true))
        .named("ClientRepository.findActiveById")
        .build();
}
```

Implemented R16 generated facade style for primary-key lookup:

```java
public Optional<ClientSummary> findById(long id) {
    QClient.Read client = CLIENT.read(renderer);

    MortarBoundQuery<QClient.FindByIdRow> query = client.findById(id)
        .named("ClientRepository.findById");

    return jdbcClient.fetchOptional(query)
        .map(row -> new ClientSummary(row.id(), row.name()));
}
```

Expected visible SQL from the same object:

```java
QClient.Read client = CLIENT.read(renderer);
MortarBoundQuery<QClient.FindByIdRow> query = client.findById(7L)
    .named("ClientRepository.findById");

assertThat(query.sql())
    .isEqualTo("select c.id, c.name, c.active from clients c where c.id = ?");
assertThat(query.parameterTypes())
    .containsExactly(Long.class);
```

Deferred generated projection style:

```java
public Optional<ClientSummary> findById(long id) {
    MortarBoundQuery<ClientSummary> query = CLIENT.read(renderer)
        .findById(id)
        .projectRecord(ClientSummary.class, c -> c.id, c -> c.name)
        .named("ClientRepository.findById");

    return jdbcClient.fetchOptional(query);
}
```

Generated `Read` facade projections are not part of the implemented R16 API
budget. They remain deferred until a later real-query corpus proves the need.

Deferred R17 style for optional filters and stable pagination:

```java
public List<ClientSummary> search(ClientSearchFilter filter, int page, int size) {
    QClient.Read client = CLIENT.read(renderer);

    MortarBoundQuery<ClientSummary> query = client.findBy()
        .projectRecord(ClientSummary.class, c -> c.id, c -> c.name)
        .whereIfPresent(filter.name(), (c, name) -> c.name.containsIgnoreCase(name))
        .whereIfPresent(filter.active(), (c, active) -> c.active.eq(active))
        .orderBy(c -> c.id.asc())
        .page(MortarPage.of(page, size))
        .named("ClientRepository.search");

    return jdbcClient.fetch(query);
}
```

Deferred R17 `exists` and `count`:

```java
public boolean existsActiveClient(long id) {
    MortarBoundScalar<Boolean> query = CLIENT.read(renderer)
        .exists(c -> Predicate.and(List.of(c.id.eq(id), c.active.eq(true))))
        .named("ClientRepository.existsActiveClient");

    return jdbcClient.fetchOne(query);
}

public long countActiveClients() {
    MortarBoundScalar<Long> query = CLIENT.read(renderer)
        .count(c -> c.active.eq(true))
        .named("ClientRepository.countActiveClients");

    return jdbcClient.fetchOne(query);
}
```

Deferred R17 simple read-model join, only when relation metadata can produce
typed target columns without string paths:

```java
public List<ClientAccountRow> findActiveClientAccounts() {
    return jdbcClient.fetch(
        CLIENT.read(renderer)
            .join(c -> c.account)
            .projectRecord(
                ClientAccountRow.class,
                c -> c.id,
                c -> c.name,
                account -> account.name
            )
            .where(c -> c.active.eq(true))
            .orderBy(c -> c.id.asc())
            .named("ClientRepository.findActiveClientAccounts")
    );
}
```

Rejected R16 write facade:

```java
public int deactivate(long id) {
    // Rejected for R16: keep writes on the explicit mutation DSL until R17
    // proves there is a real-query corpus need for generated write helpers.
    MortarBoundMutation mutation = CLIENT.write(renderer)
        .update()
        .set(c -> c.active, false)
        .where(c -> c.id.eq(id))
        .named("ClientRepository.deactivate");

    return jdbcClient.execute(mutation);
}
```

Repository usage stays explicit. The facade can reduce construction ceremony,
but the repository still owns business method names, row-to-DTO mapping, query
names, transactions, and tests:

```java
@Repository
public final class ClientRepository {
    private final MortarJdbcClient jdbcClient;
    private final QueryRenderer renderer;

    public ClientRepository(MortarJdbcClient jdbcClient, QueryRenderer renderer) {
        this.jdbcClient = jdbcClient;
        this.renderer = renderer;
    }

    public Optional<ClientSummary> findById(long id) {
        return jdbcClient.fetchOptional(
                CLIENT.read(renderer)
                    .findById(id)
                    .named("ClientRepository.findById")
            )
            .map(row -> new ClientSummary(row.id(), row.name()));
    }
}
```

The same query must be testable without hitting a database:

```java
MortarBoundQuery<QClient.FindByIdRow> query = CLIENT.read(renderer)
    .findById(7L)
    .named("ClientRepository.findById");

MortarSqlAssertions.assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);
```

SQL snapshot expectations stay visible:

```bash
mortar snapshot check --file mortar.sql.snap.json
```

VS Code hover should show the rendered SQL over the generated query call once
processor-emitted source maps exist. R16 defines the source-map/query-id
contract; R18 hardens call-site hover and navigation against real generated
contracts:

```java
CLIENT.read(renderer)
    .findById(id);
```

Expected hover content:

```sql
select c.id, c.name from clients c where c.id = ?
```

### Ergonomics Criteria

R16 succeeds only if it improves bounded read repository code without reducing
traceability:

- fixed single-table read shapes require fewer user-written lines than the
  current DSL and less ceremony than equivalent raw SQL plus manual row mapping;
- repositories no longer need to construct `SimpleMortarDb` or generated
  parameter records for common reads;
- IDE autocomplete starts from generated `Q*` fields and facade methods, then
  leads the user through the approved fixed read method set;
- Java field renames fail at compile time or annotation-processor time, not as
  runtime string query surprises;
- generated SQL is available from the query object, generated-source Javadocs,
  tests, snapshots, logs, and editor hover;
- no hidden lazy loading, implicit aggregate loading, object identity tracking,
  dirty checking, or repository magic;
- no implicit runtime query construction from method names;
- no execution methods on generated query objects; execution stays in
  `MortarJdbcClient`;
- no generated writes, batches, optional-filter matrices, joins, projections,
  `count`, or `exists` in R16;
- no custom LSP completion engine in R16;
- no public performance claim is made without retained benchmark evidence and
  reviewer sign-off.

### Architecture Boundaries

R16 must preserve the existing module contract:

- `java/core` remains framework-free. It can host small query-plan and builder
  contracts only if they do not depend on Spring, JDBC, JPA, PostgreSQL, VS Code,
  IntelliJ, or Rust.
- `java/processor` can generate Java-first facades, row types, parameter
  objects, query IDs, source-map metadata contracts, and metadata. It must not
  render SQL itself; generated query source must call a supplied `QueryRenderer`.
- `java/dialect-postgres` renders SQL from core specs and mutation specs. It
  does not execute queries or own repository semantics.
- `java/runtime-jdbc` executes rendered/generated plans and can provide bounded
  execution conveniences for generated bound queries. It must not own query
  semantics or Spring transaction policy.
- `java/spring-boot-starter` wires beans and properties only. It must not add a
  Spring Data-like repository derivation layer.
- `java/testkit` asserts SQL, parameters, metadata, snapshots, and EXPLAIN
  output without taking a Spring or JDBC dependency unless a new ADR approves
  that boundary change.
- VS Code and LSP consume processor metadata, SQL snapshots, and source maps.
  R16 may define the source-map contract; R18 owns hardening hover/navigation
  against real generated contracts. Editors do not decide query semantics or
  invent SQL.
- Rust remains CLI/compiler/LSP/reporting infrastructure and is not required in
  the default Java/Spring per-query runtime path.

### Generated Query Facades

Generated code should reduce friction, not create an unbounded public API.

Allowed R16 generated artifacts:

- one generated read entry point per entity, such as `QClient.Read` or an
  adjacent `QClientReads`, not a combinatorial method family;
- fixed read facades for `findById` and `findAll`;
- bound parameter objects where they materially improve SQL visibility or JDBC
  execution, but repositories should not usually instantiate them manually;
- projection row types for all mapped columns used by fixed `findById` and
  `findAll` reads;
- query-id and source-map contract records linking generated facade methods,
  Java field metadata, rendered SQL, and snapshot names.

API-surface budget:

- at most one generated read namespace per entity;
- fixed R16 method set: `findById`, `findAll`, `named`, `sql`, `rendered`,
  `parameterTypes`, and `metadata`;
- no overload matrix for optional filter combinations;
- no generated write namespace in R16;
- no generated projection methods in R16;
- no generated execution methods such as `query.fetch(jdbcClient)`,
  `query.fetchOptional(jdbcClient)`, `query.count(jdbcClient)`, or
  `query.exists(jdbcClient)`.

Required explicit choices:

- query name through `named(...)`;
- row-to-DTO mapping in the repository after execution;
- `orderBy(...)` before stable pagination when R17 adds paged queries;
- join path and join type when R17 adds read-model joins;
- update columns and predicates remain on the explicit mutation DSL in R16;
- unsafe raw SQL with explicit `unsafe` naming;
- SQL snapshot assertion names.

Generation limits:

- do not generate `findByNameAndStatusAndCreatedAt` combinations;
- do not generate repository classes or Spring Data repository adapters;
- do not generate aggregate loaders or navigation methods that execute SQL;
- do not generate methods for every possible projection permutation;
- do not generate facade projection methods in R16;
- do not generate facade methods for relations in R16;
- keep generated public names predictable: `Read`, `Row`, `FindById`,
  and `FindAll`.

### VS Code And LSP Requirements

R16 editor goals must be realistic. Java autocomplete comes from generated Java
source, not custom LSP semantic completion. R16 should define the metadata and
source-map contract needed for future SQL visibility. R18 should harden
call-site hover/navigation against real generated contracts.

R16 VS Code/LSP goals:

- define a stable query-id/source-map metadata contract that can link generated
  read facades, Java fields, rendered SQL, and snapshot names;
- keep copy SQL, explain SQL, and snapshot code actions on existing command
  boundaries;
- keep current marker-backed hover as a transitional path until R18 replaces it
  with source-map-backed behavior;
- define stale metadata diagnostics as an R18 implementation target.

Out of scope for R16:

- full Java semantic completion implemented by Rust;
- editor-owned query semantics;
- database connections inside the LSP server;
- arbitrary SQL parser completion for raw SQL strings;
- guaranteed hover for every hand-built `QuerySpec` unless it has a name and
  matching snapshot/source-map metadata.

### Testing Strategy

R16 implementation must follow failing-test-first expectations:

- processor generation tests for each R16 facade shape, generated row/parameter
  type, query-id/source-map contract, Javadoc, and metadata file entry;
- compile-fail or `javac` diagnostics tests for invalid field references,
  missing IDs, ambiguous projection mapping, and invalid SQL identifiers;
- runtime-jdbc tests for generated bound query execution, parameter binding,
  row mapping, and at-most-one-row behavior;
- PostgreSQL dialect tests for SQL rendering of the fixed read query shapes;
- Spring example tests that prove repository usage is shorter without leaking
  Mortar types into domain-facing ports;
- SQL snapshot tests for generated facades and named repository queries;
- Rust metadata parser tests for the query-id/source-map contract where
  applicable;
- no fake verification evidence. Planning updates may list intended tests, but
  implementation updates must include actual command output and dates.

### R16 Slices

#### R16.1: Contract, ADR, And API Budget

Objective: define the minimal generated read facade contract, bound query type,
SQL visibility contract, query-id/source-map metadata contract, and public API
budget before adding any new generated shape.

Affected modules/docs: `docs/plan.md`, `docs/roadmap.md`, `docs/spec`,
`docs/adr` if the public runtime contract changes, `java/core`,
`java/runtime-jdbc`, `java/processor`, `docs/metadata.md`, `docs/lsp.md`.

Expected tests: failing compile/generation tests for the proposed generated API
shape, runtime contract tests for `sql()`, `rendered()`, `parameterTypes()`,
and metadata visibility, plus source-map JSON parser tests if a new format is
introduced.

Non-goals: no joins, no optional filters, no VS Code UI work, no generated
writes, no Spring repository generation, no application migration.

Acceptance criteria: one minimal facade shape can expose rendered SQL and
metadata without the processor rendering SQL or the runtime owning query
semantics.

Status: Done on 2026-06-01.

Completed scope:

- Added ADR-0005 for the R16 bound read-query contract and API budget.
- Added `MortarBoundQuery<T>` in `java/core` as a framework-free named
  rendered read-query inspection contract.
- Added `MortarJdbcBoundQuery<T>` in `java/runtime-jdbc` as a JDBC row-mapping
  wrapper with no execution methods.
- Extended processor metadata with minimal query-id/generated-source entries for the
  existing generated `findAll` and `findById` executor shapes.
- Extended Rust metadata parsing for optional query metadata entries.
- Added a processor guard test proving the R16.2 `Read` facade namespace is not
  generated in R16.1.
- Extended the testkit to assert SQL from `MortarBoundQuery<?>`.

R16.1 architecture debate outcome:

- The xhigh architecture challenge found the original slice could become too
  broad if one public type mixed core inspection, JDBC binding, row mapping,
  metadata, and editor behavior.
- The accepted design splits core inspection from JDBC row mapping.
- Query IDs are justified now; full source-map-backed hover/navigation remains
  R18 scope.
- Generated API budget is documented now and enforced by a negative processor
  test in R16.1; complete generated API-budget enforcement belongs to R16.2
  golden tests when the new facade namespace exists.

Verification evidence:

- `gradlew.bat check --no-daemon` passed on 2026-06-01.
- From `rust`, `cargo fmt --all --check` passed on 2026-06-01.
- From `rust`, `cargo clippy --all-targets --all-features -- -D warnings`
  passed on 2026-06-01.
- From `rust`, `cargo test` passed on 2026-06-01.
- From `editors/vscode`, `bun run typecheck` passed on 2026-06-01.

#### R16.2: Fixed Single-Table Read Facades

Objective: replace manual generated parameter-record usage for `findById` and
`findAll` with shorter bound query facades while preserving current generated
executor performance and SQL visibility.

Affected modules/docs: `java/processor`, `java/runtime-jdbc`, `java/testkit`,
`examples/spring-boot-postgres`, `docs/api-reference.md`,
`docs/usage-guide.md`, `docs/roadmap.md`.

Expected tests: processor golden tests, repository example tests, SQL assertion
tests, runtime generated-query tests, and compile-fail tests for invalid IDs or
missing identifiers.

Non-goals: no dynamic method-name queries, no relation loading, no generated
repository classes.

Acceptance criteria: repository code for `findById` and `findAll` is shorter
than R15, generated SQL remains directly inspectable, and existing R15 executor
behavior remains covered.

Status: Done on 2026-06-02.

R16.2 architecture debate outcome:

- The xhigh architecture challenge recommended the singular generated namespace
  `Read` on each `Q*` metamodel instance. `Reads` sounded like an unbounded
  convenience bag, and `Query` collided with existing query terms.
- `read(renderer)` belongs on the generated metamodel for discoverability and
  keeps the facade entity-local.
- The generated facade must return framework-free `MortarBoundQuery<T>` values,
  not JDBC adapters, row mappers, generated repositories, or execution methods.
- `findAll()` remains included because the method is already explicit and is
  documented for small reference-data reads; diagnostics and future corpus work
  can add stronger warnings without renaming it.
- `.named(...)` belongs on the immutable bound query value. It returns a new
  query object and does not mutate facade state.
- Projection support remains outside R16.2. `projectRecord` and `projectDto`
  stay planned for later proof instead of expanding this slice.
- The facade only meaningfully reduces repository ceremony if
  `MortarJdbcClient` can explicitly execute bound queries. R16.2 therefore
  includes a narrow runtime execution bridge for `MortarBoundQuery<T>` and
  `MortarJdbcBoundQuery<T>` while preserving the rule that query objects do not
  execute themselves.
- The accepted method set stays within ADR-0005: `read(renderer)`,
  `findById(id)`, `findAll()`, immutable `named(...)`, and inspection methods.

Completed scope:

- Generated one `Q*.Read` namespace per entity.
- Added `read(renderer).findById(id)` returning
  `MortarBoundQuery<Q*.FindByIdRow>` with the supplied identifier bound into
  the rendered query.
- Added `read(renderer).findAll()` returning
  `MortarBoundQuery<Q*.FindAllRow>` for explicit full-table reads.
- Added immutable `named(...)` to `MortarBoundQuery<T>` and
  `MortarJdbcBoundQuery<T>`.
- Added explicit `MortarJdbcClient.fetch(...)` and `fetchOptional(...)`
  overloads for bound queries so repositories execute through the runtime
  adapter without self-executing query objects.
- Updated processor metadata to point canonical generated-source members at
  `read.findById` and `read.findAll` on `Q*.Read` while keeping query IDs and
  snapshot keys stable.
- Updated `examples/spring-boot-postgres` to use the R16.2 facade for common
  reads.

Focused verification before final gate:

- `gradlew.bat :java:core:test --tests dev.mortar.core.MortarBoundQueryTest :java:runtime-jdbc:test --tests dev.mortar.jdbc.MortarJdbcBoundQueryTest --tests dev.mortar.jdbc.MortarJdbcClientTest :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon`
  failed first because `MortarBoundQuery.named(String)` did not exist, then
  passed after implementation.
- `gradlew.bat :java:processor:test --tests dev.mortar.processor.MortarProcessorMetadataFileTest --no-daemon`
  passed after metadata was updated for the `Read` facade.
- Final R16.2 verification passed on 2026-06-02:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`,
  and `cargo test`; from `editors/vscode`, `bun run typecheck`;
  `git diff --check`; and the private path scrub excluding build outputs and
  caches.

#### R16.3: Bound Parameters, Visible SQL, And Testkit Contract

Objective: make fixed generated read facades first-class runtime and testkit
inputs so repositories can bind values, inspect SQL, assert metadata, and write
snapshots without custom glue code.

Affected modules/docs: `java/core`, `java/processor`, `java/testkit`,
`examples/spring-boot-postgres`, `docs/api-reference.md`,
`docs/usage-guide.md`, `docs/getting-started.md`, `docs/testkit.md`.

R16.3 architecture debate outcome:

- `MortarBoundQuery<T>` remains the sole framework-free bound read-query
  inspection contract. R16.3 does not introduce a second visible-SQL
  abstraction.
- `java/testkit` treats `MortarBoundQuery<?>` as the canonical public SQL
  assertion input and keeps `RenderedQuery` as the lower-level fallback. The
  public assertion API stays small: SQL text, parameter values, parameter
  types, and metadata.
- Bound parameter typing stays owned by `java/core`. If null-bearing parameters
  need explicit type visibility, that belongs in core rather than a
  JDBC-specific wrapper. Generated `findById(id)` keeps fail-fast null
  rejection for boxed ids and must render exactly one typed identifier
  parameter.
- Generated `Q*.Read` facades remain inspection-only. Execution stays
  adapter-owned in `MortarJdbcClient`; no generated or core query object gains
  self-executing methods.
- Docs/examples should show Java-first repository tests asserting
  `assertThatSql(query)`, `query.parameterTypes()`, and `query.rowType()`
  directly from the generated facade. Treat `query.rendered()` as a lower-level
  escape hatch, not the canonical public example.

Expected tests: core bound-query tests, testkit assertion tests for bound
queries, processor generated-facade tests, and repository SQL assertion tests.

Non-goals: no optional filters, no count/exists, no joins, no generated writes,
no benchmark claims, no snapshot auto-approval.

Acceptance criteria: a generated read query can be rendered, asserted,
snapshotted, diffed, and executed through `MortarJdbcClient` with no execution
method on the generated query object.

Completed scope:

- Strengthened `MortarSqlAssertions.assertThatSql(MortarBoundQuery<?>)`
  failure messages with query name and row type context while keeping the public
  assertion API unchanged.
- Added bound-query testkit coverage for SQL, parameter values, parameter
  types, metadata, and bound-query-specific failure context.
- Added repository-facing generated facade coverage proving
  `QClient.CLIENT.read(renderer).findById(7L)` exposes the supplied identifier
  as the rendered parameter, exposes `Long.class` as the parameter type,
  exposes metadata and row type, and rejects null boxed identifiers before
  rendering SQL.
- Updated public docs to show `assertThatSql(query)` as the canonical
  generated-facade assertion path and cleaned public-doc scrub false positives.

Focused verification:

- `gradlew.bat :java:testkit:test --tests dev.mortar.testkit.MortarSqlAssertionsTest --no-daemon`
  failed first on 2026-06-02 because bound-query assertion failures did not
  include query name and row type, then passed after implementation.
- `gradlew.bat :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon`
  passed for generated facade SQL, parameter value, parameter type, metadata,
  row type, and null-id boundary behavior.
- `gradlew.bat :java:core:test --tests dev.mortar.core.MortarBoundQueryTest :java:testkit:test --tests dev.mortar.testkit.MortarSqlAssertionsTest :java:processor:test --tests dev.mortar.processor.MortarProcessorGenerationTest :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon`
  passed after the focused failure was fixed.

Final R16.3 verification passed on 2026-06-02:
`gradlew.bat check --no-daemon`; from `rust`,
`cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`,
and `cargo test`; from `editors/vscode`, `bun run typecheck`;
`git diff --check`; and the private path scrub excluding build outputs and
caches.

Architecture note: R16.3 did not add generated execution methods, repositories,
JDBC dependencies in `java/core`, or a second visible-SQL abstraction. Runtime
execution remains adapter-owned through `MortarJdbcClient`.

#### R16.4: Examples And Usage Guidance

Objective: update public examples and docs so users can see when to use fixed
generated read facades, current DSL, snapshots, and explicit unsafe SQL.

Affected modules/docs: `examples/spring-boot-postgres`,
`examples/clean-architecture-postgres`, `docs/getting-started.md`,
`docs/spring-boot-postgres-example.md`,
`docs/ddd-clean-architecture-example.md`, `docs/usage-guide.md`,
`docs/api-reference.md`, `README.md`, `docs/roadmap.md`.

Expected tests: Spring example tests, Clean Architecture example checks, docs
path scrub, and final root verification.

Non-goals: no migration of existing Sequel apps, no release, no tag, no publish,
no public performance claim, no generated repository classes.

Acceptance criteria: public examples compile in CI, demonstrate shorter
repository code, keep SQL visible through tests and snapshot docs, and prove
domain-facing ports stay Mortar-free.

Status: Done on 2026-06-02.

R16.4 architecture debate outcome:

- The xhigh pre-edit challenge approved R16.4 as a docs/examples convergence
  slice around the shipped R16.2/R16.3 surface.
- The accepted correction was to move the Clean Architecture example from the
  legacy direct generated executor plus parameter record path to the canonical
  `Q*.read(renderer).findById(id).named(...)` bound-query flow.
- Public guidance must keep `MortarGeneratedQuery` as an advanced hot-path
  contract, not the canonical repository path.
- Generated projections, joins, optional filters, `count`, `exists`, writes,
  generated repositories, self-executing query objects, and source-map/LSP
  hardening remain outside R16.4.

Completed scope:

- Updated `examples/clean-architecture-postgres` so
  `PostgresClientReader.findById(...)` uses the generated `Read` facade,
  executes through `jdbcClient.fetchOptional(query)`, and maps generated rows
  back to the domain-facing `ClientSummary`.
- Added Clean Architecture adapter tests that assert the generated bound query
  SQL, parameter value, and parameter type through `assertThatSql(query)`.
- Refined public usage, Spring Boot, Clean Architecture, API reference, and
  getting-started docs so Spring users can see when to use generated `Read`
  facades, how SQL remains visible, how repository tests catch query drift, and
  which R16 capabilities are intentionally not generated.
- Corrected canonical planning to keep generated facade projections deferred
  instead of implying they are part of the implemented R16 API budget.

Focused verification:

- `gradlew.bat :examples:clean-architecture-postgres:test --tests dev.mortar.examples.cleanpostgres.PostgresClientReaderTest --no-daemon`
  failed first because the adapter still used `CLIENT.findById(renderer)` with
  `FindByIdParameters`, then passed after the adapter moved to
  `CLIENT.read(renderer).findById(id).named(...)`.
- `gradlew.bat :examples:spring-boot-postgres:test --tests dev.mortar.examples.springpostgres.ClientRepositoryTest --no-daemon`
  passed on 2026-06-02.
- `gradlew.bat :examples:clean-architecture-postgres:check --no-daemon`
  passed on 2026-06-02.

Final R16.4 verification passed on 2026-06-02:
`gradlew.bat check --no-daemon`; from `rust`,
`cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`,
and `cargo test`; from `editors/vscode`, `bun run typecheck`.
`git diff --check` and the private path scrub are recorded in the final change
review for this slice.

Architecture note: R16.4 did not add generated execution methods, writes,
joins, optional filters, `count`, `exists`, projections, generated
repositories, or editor/LSP source-map hardening. `MortarJdbcClient` remains
the execution boundary and domain-facing ports remain Mortar-free.

### Risks And Rejected Options

- Not just jOOQ: jOOQ is mature and excellent for SQL-first type-safe query
  construction. Mortar's target is Java-domain-first authoring with generated
  Java metamodels, Spring adapter ergonomics, and SQL transparency as a product
  feature. R16 should not copy jOOQ's full SQL DSL surface.
- Not raw SQL: raw SQL remains transparent, but Java refactors, DTO changes,
  and column changes can break string queries late. Mortar should keep SQL
  visible while moving authoring to typed Java references.
- Not JPA Criteria: Criteria proves type-aware query APIs can exist, but its
  verbosity is exactly the ergonomics failure R16 must avoid.
- Not repository magic: Spring Data-style method derivation is productive for
  simple cases, but it hides query construction behind names and can become
  opaque. Mortar repositories should remain explicit infrastructure adapters.
- Not ORM behavior: R16 must not add lazy loading, identity maps, dirty
  checking, aggregate loading, cascade persistence, or hidden transaction
  policy.
- Not migration now: existing Sequel application migration waits until R17
  proves R16 against a public query corpus and R18 hardens refactor safety and
  tooling. R16 is design and product-surface work only.
- Generated API explosion: the biggest R16 risk is producing a public generated
  surface larger than users can understand. Favor one small generated facade and
  typed builders over method combinations.
- Editor overreach: editors may use generated-source metadata as tooling input.
  They must not become a second query compiler or promise completion that
  normal generated Java code cannot provide.

### Sequel Standards Preserved

R16 implementation must explicitly preserve:

- no dead code;
- no boilerplate filler;
- no quick hacks;
- no backwards compatibility hacks without an ADR;
- DDD and Clean Architecture boundaries;
- no wildcard imports;
- tests before claiming done;
- no public performance claims without evidence;
- no product code marked ready without compile, tests, quality gate, and docs
  evidence.

### Architecture Debate Outcome

An xhigh architecture subagent challenged the R16 design before this plan was
finalized. The critique narrowed R16 in six ways:

- Mortar can realistically beat handwritten SQL plus JDBC binding and row
  mapping for bounded read families, not for every query. Fixed single-table
  reads stay in R16; optional filters, joins, richer projections, count, exists,
  and writes move behind R17's real-query corpus gate.
- Generated facade growth is the main public API risk. R16 now has an explicit
  API-surface budget: one generated read namespace per entity, a fixed read
  method set, no overload matrices, no generated write namespace, and no
  generated execution methods.
- VS Code autocomplete should mostly come from generated Java source. R16
  defines query-id/source-map metadata; R18 hardens source-map hover/navigation
  against real generated contracts.
- Clean Architecture is preserved only if generated queries do not execute
  themselves. `query.fetchOptional(jdbcClient)` style APIs are rejected;
  `MortarJdbcClient` remains the execution adapter.
- ORM risk is real if R16 grows into aggregate loading, relation traversal,
  identity tracking, dirty checking, cascades, or generated write/batch
  behavior. Those remain non-goals.
- The original R16 slices were too broad. R16 is split into contract/API budget,
  fixed single-table reads, bound-parameter visible-SQL/testkit contract, and
  examples/docs. Broader query authoring moves to R17; editor hardening moves
  to R18.

## R17 Canonical Design: Real-Query Coverage Gate

Status: Done

R17 is a planning and evidence gate, not a feature bucket. It must not be
marked `In Progress` until a public fixture app, query corpus, or implementation
exists. It must not be marked `Done` until the corpus is implemented, the
queries are tested, decisions are recorded, and the final verification evidence
exists.

### Problem Statement

R16 made the fixed read path easier without expanding the generated surface
beyond `read(renderer).findById(id)`, `read(renderer).findAll()`, immutable
`.named(...)`, visible SQL, testkit assertions, and explicit
`MortarJdbcClient` execution. That surface is intentionally small.

The next risk is adding generated methods because they look useful in abstract
planning rather than because realistic repository code proves they reduce
friction. Optional filters, joins, projections, stable pagination, `count`, and
`exists` can quickly become an API explosion or an ORM-like abstraction if they
are generated without evidence.

R17 exists to build that evidence with public fixtures before Mortar adds more
generated Java-first API.

### Why R17 Follows R16

R16 answered whether fixed single-table reads can be shorter while keeping SQL
visible. R17 asks a different question: when real repositories need broader
query shapes, is the existing explicit DSL already the right answer, or is
there repeated pain that justifies a small generated addition?

R17 must preserve the R16 constraints while collecting evidence:

- generated APIs remain inspection-only;
- `MortarJdbcClient` remains the execution boundary;
- domain ports remain Mortar-free;
- generated method count must not grow with filter, projection, or join
  combinations;
- editor/source-map hardening remains R18 scope;
- no existing application migration is authorized.

### Public Fixture Strategy

R17 uses a small public mini-domain, not private application code and not a
renamed private migration. The fixture should be realistic enough to exercise
repository decisions but small enough to maintain.

The fixture should model a neutral operational domain with two or three
bounded contexts and clear read use cases. Suitable public examples include an
order desk, library circulation, or service-ticket workflow. The chosen fixture
must include:

- at least four annotated entities;
- at least two relationships with explicit generated relationship metadata;
- one small reference table where `findAll()` remains appropriate;
- repository/service ports that expose business methods and DTOs, not Mortar
  query types;
- infrastructure adapters that own `Q*`, `QuerySpec`, `MortarBoundQuery<?>`,
  `MortarJdbcClient`, snapshots, and SQL tests;
- seed data and schema small enough for fast CI;
- stable query names and snapshot keys for every corpus query;
- documented expected SQL shape, parameter values, parameter types, metadata,
  and compile-failure behavior.

The fixture should be multi-module or structured so R18 can reuse it for Gradle
incremental compilation, metadata drift, rename/delete refactor failures, and
editor/source-map hardening.

### Query Families To Evaluate

R17 evaluates query families through realistic repository flows. A query enters
the corpus only when it has a user-visible reason in the mini-domain.

- **Fixed lookup and reference reads:** keep R16 `Read` facade examples as the
  baseline for comparison.
- **Optional filters:** search screens with nullable request fields, empty
  strings, enum/status filters, date ranges, and explicit skipped filters.
- **Multi-predicate reads:** fixed business predicates such as active records,
  ownership, status, and tenant-like scoping in one repository method.
- **Joins through generated relationship metadata:** explicit read-model
  joins with named relation paths and join type, without aggregate loading or
  implicit traversal.
- **DTO/record projections:** repository methods that return domain-facing
  summaries, detail records, or read models without exposing generated row
  types to callers.
- **Stable pagination:** paged list and search flows that require deterministic
  `orderBy(...)` before pagination.
- **Count:** count rows for a filtered list only when the use case actually
  needs a total.
- **Exists:** existence checks for business validation without loading rows.
- **Repository-level SQL drift tests:** adapter tests that fail when SQL,
  parameter values, parameter types, metadata, or query names drift.
- **Snapshot/testkit expectations:** snapshot names, `assertThatSql(...)`
  expectations, and CLI snapshot checks for every corpus query that becomes a
  canonical fixture.

### API Decision Framework

Every R17 query family is evaluated with the same decision rules.

Generated facade support qualifies only when all are true:

- the same query family appears across multiple fixture use cases;
- the generated shape is materially shorter or safer than the explicit DSL;
- the public method count stays bounded and predictable;
- the generated API returns framework-free inspectable query values;
- execution still happens through `MortarJdbcClient` or another adapter;
- SQL, parameters, parameter types, metadata, and query name remain directly
  testable;
- Clean Architecture ports remain free of Mortar types;
- processor metadata can represent the query without requiring R18
  source-map hardening first.

The explicit DSL remains the default when:

- the query is one-off or domain-specific;
- the query needs custom predicates, joins, projections, or ordering that are
  clearer as explicit Java;
- a generated API would require overload combinations;
- the readability gain is marginal;
- the shape pressures relation traversal, aggregate loading, implicit joins,
  hidden pagination defaults, or repository generation.

More evidence is required when:

- only one fixture use case needs the shape;
- the generated API needs scalar query types such as `count` or `exists`;
- aliasing, projection naming, or join metadata becomes ambiguous;
- the shape would require new metadata consumed by R18 tooling;
- the implementation would change public runtime contracts.

Reject the design when:

- method count grows with the number of optional fields or join combinations;
- generated method names derive queries from repository method names;
- generated query objects execute themselves;
- generated repositories or Spring Data-style adapters appear;
- relation traversal implies aggregate loading or lazy loading;
- writes, batches, or mutation helpers are added as part of this read-query
  gate.

Default R17 decision: optional filters are DSL-first. A hybrid generated
filter-builder entry point may be considered only after the corpus proves
repeated pain and the method set remains bounded. Generated-first optional
filters and overload matrices are rejected.

### Testing Strategy

R17 implementation must use executable evidence, not aspirational examples.

- Add fixture tests that compile the public mini-domain and its infrastructure
  adapters.
- Add repository tests for every corpus method with
  `assertThatSql(query).hasSql(...).hasParameters(...).hasParameterTypes(...)`
  or the equivalent rendered-query assertion for explicit DSL queries.
- Add snapshot checks for named canonical corpus queries.
- Add Testcontainers coverage only when the claim is PostgreSQL execution or
  syntax compatibility, not for every fast repository drift test.
- Add compile-fail or processor diagnostic tests for renamed fields, deleted
  fields, invalid relationships, ambiguous projections, and missing ordering
  before pagination.
- Record query length, ceremony, drift-test readability, and boundary leakage
  observations before deciding that generated API is warranted.
- Keep R17 tests usable by R18 as golden, metadata, incremental compilation,
  source-map, and editor behavior fixtures.

### R17 Slices

#### R17.1: Fixture App And Query Corpus Contract

Objective: create the public neutral mini-domain plan, query inventory, naming
rules, snapshot keys, coverage rubric, and Clean Architecture boundaries before
any new generated API is considered.

Expected output: a documented fixture contract and planned repository flows for
fixed lookup, optional-filter search, stable page, explicit join read model,
DTO projection, `count`, `exists`, and refactor-failure cases.

Non-goals: no product API changes, no generated filters, no generated joins, no
scalar query contract, no writes, no app migration.

#### R17.2: DSL-First Corpus Implementation

Objective: implement the corpus queries with the current R16 facade plus the
explicit DSL inside infrastructure adapters, proving what already works before
adding generated surface.

Expected output: compile-backed fixture repositories, SQL drift tests,
snapshot expectations, and ergonomics notes comparing R16 fixed reads with DSL
query families.

Non-goals: no generated optional-filter API, no generated projections, no
generated joins, no `count`/`exists` public API changes.

#### R17.3: Optional Filter Decision

Objective: decide whether optional filters stay DSL-only or need one bounded
hybrid generated filter-builder entry point.

Decision rules: reject overload matrices; require repeated corpus evidence;
require explicit skipped-filter semantics; keep generated query values
inspection-only; keep execution adapter-owned.

Expected output: decision record, implementation plan if approved, or explicit
rejection/defer note if the DSL is sufficient.

#### R17.4: Joins, Projections, Pagination, Count, And Exists Decisions

Objective: evaluate broader read shapes as separate decisions instead of one
large generated facade expansion.

Required decision split:

- joins and projections: evaluate aliasing, relation metadata, read-model
  clarity, and DTO mapping without aggregate loading;
- stable pagination: require explicit ordering before page execution;
- `count` and `exists`: treat as ADR-sized because scalar results may reopen
  the visible-query and runtime execution contracts.

Expected output: decision records that say generated, DSL-only, deferred, or
rejected for each family.

#### R17.5: R18 Fixture Handoff And Roadmap Update

Objective: package the final corpus as the input to R18 hardening.

Expected output: R18-ready fixture inventory covering generator golden tests,
metadata diagnostics, rename/delete failures, schema drift, incremental
compilation, snapshot workflow, source-map-backed hover/navigation, and editor
behavior against real generated contracts.

### R17 Exit Criteria

R17 can be marked `Done` only when:

- a public non-application-specific fixture corpus exists and compiles in CI;
- every corpus query has a named repository use case and adapter-boundary SQL
  drift test;
- snapshot/testkit expectations are recorded for canonical corpus queries;
- optional filters, joins, projections, stable pagination, `count`, and
  `exists` have explicit generated/DSL/defer/reject decisions;
- no generated API expands beyond evidence-backed decisions;
- no generated repositories, generated writes, optional-filter overload
  matrices, self-executing query objects, or ORM behavior are introduced;
- Clean Architecture boundaries are verified: domain and application ports
  remain Mortar-free;
- R18 receives a concrete fixture handoff for tooling and refactor-safety
  hardening;
- verification evidence is recorded in the roadmap.

### R17 Risks

- Optional-filter overloads can create an unbounded generated API.
- A weak synthetic corpus can make generated API look useful without proving
  real repository value.
- Joins and projections can pressure aliasing, metadata, and source-map
  contracts before R18 hardens them.
- `count` and `exists` can force a second visible-query abstraction or awkward
  scalar mapping if treated as small conveniences.
- Clean Architecture can drift if generated query types leak into domain ports
  or services.
- Feature parity pressure with Spring Data, jOOQ, QueryDSL, or ORM tools can
  pull Mortar away from Java-first SQL transparency.

### R17 Architecture Debate Outcome

The required xhigh R17 challenge concluded that R17 should stay an evidence
gate, not a feature bucket.

- The existing DSL already covers projections, predicates, joins, ordering, and
  paging, so R17 should prove those shapes against real repository flows before
  generating anything.
- The Clean Architecture example shows the desired split: generated fixed reads
  for common lookups and explicit DSL for broader paged queries inside
  infrastructure adapters.
- Optional filters should default to DSL-first, with a bounded hybrid generated
  filter builder considered only if repeated public fixture evidence proves
  the need. Generated-first optional filters and overload matrices are
  rejected.
- `count` and `exists` should be separate ADR-sized decisions because scalar
  query results may reopen the R16 visible-SQL and runtime contracts.
- The fixture must be public and neutral, not private application code or a
  disguised migration.
- R17 should deliberately feed R18 by preserving stable query names, snapshot
  keys, expected SQL, metadata, compile-failure cases, and fixture structure
  for future tooling/refactor-safety hardening.

### R17 Execution Plan

The R17 implementation uses a public service-ticket query corpus with
compile-separated fixture modules:

- `examples/query-corpus-domain`: domain vocabulary only; no Mortar
  dependencies.
- `examples/query-corpus-application`: business ports, criteria, DTO records,
  and service flow; no Mortar dependencies.
- `examples/query-corpus-infrastructure-postgres`: annotated persistence
  models, generated `Q*` metadata, PostgreSQL adapter queries, SQL drift tests,
  and snapshot fixture data.

The fixture includes four annotated entities: ticket, customer, technician, and
ticket status. Ticket-to-customer, ticket-to-technician, and ticket-to-status
relations exercise generated relationship metadata while keeping joins
explicit in adapter DSL code. Ticket status is the reference table used for the
existing R16 generated `findAll()` baseline.

Implementation sequence:

1. Add failing fixture tests for module boundaries, generated fixed reads,
   optional-filter DSL searches, explicit join projections, stable ordered
   pagination, and R18 snapshot inventory.
2. Implement only the public fixture modules and adapter code needed to pass
   those tests. Do not change product API modules.
3. Record the R17 query-family decisions in an ADR. Optional filters,
   multi-predicate reads, joins, projections, and stable pagination remain
   DSL-first in R17. Generated optional-filter overload matrices, generated
   repositories, generated writes/batches, self-executing queries, implicit
   traversal, aggregate loading, and ORM behavior remain rejected. `count` and
   `exists` are deferred as scalar-contract decisions.
4. Package the R18 handoff with stable query names, snapshot keys, expected
   SQL, parameter values, parameter types, metadata expectations,
   rename/delete failure cases, schema drift cases, and editor/source-map
   needs.

Stable pagination is evaluated through corpus tests and diagnostics, not by
silently changing the core DSL in R17. Corpus page queries must call
`orderBy(...)` before `page(...)`; unordered paging remains a diagnostic and a
future enforcement decision.

### R17 Completion Record

R17.1 through R17.5 were completed through the public service-ticket query
corpus and ADR-0006.

Completed scope:

- added compile-separated domain, application, and PostgreSQL infrastructure
  fixture modules;
- implemented four annotated public fixture entities and three explicit
  generated relation metadata paths;
- implemented generated fixed lookup/reference reads through the existing R16
  `Read` facade;
- implemented optional filters, multi-predicate reads, joins, projections, and
  stable ordered pages through explicit adapter-owned DSL;
- recorded canonical snapshot keys and SQL expectations in
  `examples/query-corpus-infrastructure-postgres/src/test/resources/r17-query-corpus/mortar.sql.snap.json`;
- documented the R18 fixture handoff in `docs/r17-query-corpus.md`;
- accepted ADR-0006 with no generated API expansion.

Decisions:

- optional filters: DSL-only in R17; generated overload matrices rejected;
- joins: DSL-only through explicit `RelationRef` paths; implicit traversal
  rejected;
- projections: DSL-only through explicit record/DTO projection;
- stable pagination: DSL-only with explicit ordering in corpus queries; hidden
  default ordering rejected and core enforcement deferred;
- `count` and `exists`: deferred as scalar-contract decisions;
- generated repositories, writes, batches, self-executing query objects, and
  ORM behavior: rejected.

Focused fixture verification:

- `gradlew.bat :examples:query-corpus-domain:test :examples:query-corpus-application:test :examples:query-corpus-infrastructure-postgres:test --no-daemon`
  passed on 2026-06-02.

Final verification:

- `gradlew.bat check --no-daemon` passed on 2026-06-02.
- From `rust`, `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test` passed on 2026-06-02.
- From `editors/vscode`, `bun run typecheck` passed on 2026-06-02.
- `git diff --check` passed on 2026-06-02.
- Private path and private project scrub passed with zero matches outside
  build/cache outputs on 2026-06-02.

## R18 Canonical Design: Stability, Refactor Safety And Tooling Hardening

Status: Planned

R18 is a contract-hardening gate. It is documentation-only in this planning
slice and must not be treated as an implementation record, release candidate,
or application migration signal.

### Problem Statement

R16 introduced a small generated fixed-read facade and a framework-free
visible-SQL contract. R17 proved the broader read-query families against a
public service-ticket corpus and deliberately kept optional filters, joins,
projections, and ordered pagination in the explicit DSL. That leaves a
different question for R18: whether the existing surface stays reliable when
normal Java changes invalidate generated symbols, generated metadata,
snapshots, editor output, and incremental build state.

R18 must define and later prove deterministic failure or regeneration behavior
for supported refactors. It must also separate three guarantee classes that are
often blurred together:

- Java compile-time or annotation-processor failures for unsupported stale
  entity/query code;
- post-build tooling diagnostics for stale generated metadata, source maps,
  snapshots, and editor data;
- schema drift diagnostics that compare generated metadata against a database
  schema through CLI/tooling workflows.

The forbidden outcome is silent success with stale generated `Q*` source, stale
metadata, stale SQL snapshots, or misleading editor hover/copy SQL output.

### Why R18 Follows R17

R17 created the public fixture that R18 needs. The service-ticket corpus has
compile-separated domain, application, and PostgreSQL infrastructure modules,
stable query names, stable snapshot keys, expected SQL, parameter types,
metadata assertions, relation paths, and named rename/delete/schema-drift
handoff cases.

R18 follows R17 because refactor safety and editor transparency cannot be
proved with abstract examples. They need the actual R17 corpus shape:

- domain and application modules that remain Mortar-free;
- infrastructure adapters that own generated `Q*`, DSL query specs, snapshots,
  and SQL assertions;
- fixed generated reads for `TicketReader.findHeader` and
  `TicketReader.listStatusOptions`, implemented through
  `TICKET_RECORD.read(renderer).findById(ticketId)` and
  `TICKET_STATUS_RECORD.read(renderer).findAll()`;
- explicit DSL queries for optional filters, joins, projections, and stable
  ordered pages;
- snapshot inventory at
  `examples/query-corpus-infrastructure-postgres/src/test/resources/r17-query-corpus/mortar.sql.snap.json`.

R18 is not authorized to add product API because R17 made no generated API
expansion. Any future generated API beyond fixed reads remains outside R18
unless a separate ADR and implementation slice approve it.

### Guarantees R18 Must Prove

R18 must define executable evidence for these guarantees:

- renaming an annotated entity field used by generated reads or DSL query code
  causes either regenerated consistent `Q*`/metadata output after all consumers
  are updated, or a compile-time/processor diagnostic failure while consumers
  remain stale;
- deleting a field, relation, or query metadata input causes deterministic
  failure before runtime execution, using unresolved generated symbols, missing
  relation paths, or stable processor diagnostic categories;
- changing column metadata changes SQL snapshot or SQL assertion expectations
  when the rendered SQL changes;
- stale generated metadata is detected before editor hover, navigation, or copy
  SQL presents old output as current;
- generated Java source maps connect generated fixed-read facade symbols,
  query IDs, snapshot keys, rendered SQL, and generated source locations for
  `read.findById` and `read.findAll`, including the R17 repository calls
  named `TicketReader.findHeader` and `TicketReader.listStatusOptions`;
- VS Code hover and copy SQL use source-map-backed data for real R17 fixture
  generated read calls, and fail closed with diagnostics when the data is
  stale or incomplete;
- domain and application fixture modules remain Mortar-free;
- clean and incremental Gradle builds converge to the same generated metadata,
  source-map, snapshot, and compile-failure outcomes across the fixture
  modules.

### Explicit Non-Goals

R18 does not include:

- private application migration;
- public release, release candidate, tag, publication, or performance claim;
- generated API expansion beyond the R16 fixed-read surface;
- generated optional-filter API or optional-filter overload matrices;
- generated joins, generated projections, generated repositories, generated
  writes, or generated batches;
- `count` or `exists` scalar contracts unless separately ADR-approved;
- self-executing query objects;
- ORM behavior, lazy loading, aggregate loading, identity maps, or implicit
  relation traversal;
- arbitrary DSL call-site hover/navigation;
- editor-side SQL rendering or editor-owned query semantics;
- broad editor UX, completion, refactoring commands, or quick-fix work beyond
  source-map-backed SQL transparency and stale-data diagnostics;
- IntelliJ parity work that changes the shared source-map or metadata contract
  only for IntelliJ.

### R17 Fixture Reuse Strategy

R18 should consume the R17 corpus as test input and workflow evidence, not as
an excuse to add query families or product helpers.

Required reuse:

- positive golden inventory for generated `findById`, generated `findAll`,
  relation metadata, query IDs, source maps, and snapshot keys;
- negative refactor variants for `TicketRecord.summary`,
  `TechnicianRecord.displayName`, `TicketStatusRecord.code`,
  `TicketRecord.customer`, `TicketRecord.assignedTechnician`, and
  `TicketRecord.status`;
- schema-drift cases for `tickets.status_code`, `tickets.customer_id`,
  `tickets.assigned_technician_id`, and `technicians.region`;
- editor and LSP fixtures over the real R17 generated read calls rather than a
  new toy editor workspace;
- multi-module Gradle cases that prove domain/application changes do not
  create false Mortar metadata drift and infrastructure entity changes do
  invalidate generated outputs correctly.

Temporary patched fixture variants are acceptable for tests when they are
created as test data or build fixtures. They must not become new product
examples or new public query-surface claims.

### Refactor-Safety Test Strategy

R18 refactor tests should be compile-based and semantic. They should not rely
on screenshots, exact full compiler-message snapshots, raw generated-file diffs
as the only signal, or source text rewrites with no recovery check.

Each named refactor case should use a baseline/fail/recover matrix:

1. Baseline: the unmodified R17 corpus compiles, SQL assertions pass, snapshots
   match, and metadata/source-map inventory is fresh.
2. Fail: the producer entity, field, relation, or column metadata changes while
   at least one consumer remains stale. The expected outcome is unresolved
   generated symbols, missing relation paths, or stable processor/tooling
   diagnostic categories before runtime execution.
3. Recover: consumers are updated to the new symbol or metadata state, the
   build passes, generated metadata/source maps converge, and SQL snapshot/test
   expectations either remain stable or change explicitly.

Assertions should match stable categories such as unresolved symbol, missing
relation metadata, metadata freshness failure, snapshot drift, or schema drift.
They should not depend on complete `javac` wording or line numbers that are not
part of a documented diagnostic contract.

### Generated Metadata And Source-Map Strategy

R18 should harden generated-symbol mapping for fixed reads before attempting
broader editor behavior. The source-map-backed contract should be keyed by
query identity and include enough information to connect:

- generated entity type and generated read namespace;
- generated member such as `read.findById` or `read.findAll`;
- query ID and snapshot key;
- row type and parameter metadata;
- generated Java source location or generated symbol range;
- rendered SQL or a deterministic link to the snapshot/rendered SQL that the
  editor may display.

R18 should not make the processor render SQL. Rendering remains in dialects and
runtime/test workflows. Editors and LSP consume metadata and snapshots; they do
not invent SQL or decide query semantics.

Stale metadata detection must fail closed. If generated source, metadata,
source maps, snapshot keys, and current fixture source do not agree, editor
hover/navigation/copy SQL must report a diagnostic or no result rather than
falling back silently to marker-based output.

If R18 implementation needs a new metadata format, source-map artifact,
freshness fingerprint, or cross-tool compatibility rule, that change requires
a new ADR before product code changes.

### Gradle Incremental And Multi-Module Strategy

R18 should treat Gradle incremental behavior as a correctness contract, not a
speed claim.

The fixture must prove that clean and incremental builds converge for the same
source state. Expected evidence includes:

- clean build metadata and source-map inventory matches incremental build
  inventory after supported fixture changes;
- changes in `examples/query-corpus-domain` and
  `examples/query-corpus-application` do not create false Mortar metadata
  drift because those modules contain no Mortar dependencies;
- changes in annotated infrastructure records regenerate `Q*`, metadata,
  source maps, and snapshots as expected or fail deterministically;
- stale generated metadata cannot remain apparently valid after deleting or
  renaming annotated entity members;
- multi-module dependency direction remains intact: domain and application
  modules do not depend on Mortar, and infrastructure remains the only fixture
  module with generated query metadata.

Open risk to address in R18 implementation: the processor is currently declared
as Gradle `isolating`, while it also emits a shared
`META-INF/mortar/entities.json` inventory. R18 must verify that the
classification is correct for the actual generated metadata behavior or change
the processor/build contract through an ADR-backed implementation slice.

### VS Code And Editor Strategy

The primary R18 editor target is VS Code because it exercises the shared Rust
LSP path used by the current VS Code extension. IntelliJ remains secondary.

R18 VS Code scope:

- source-map-backed hover for R17 generated fixed-read calls;
- copy SQL using the same source-map-backed data;
- explicit mapping from `TicketReader.findHeader` to generated
  `read.findById` metadata and from `TicketReader.listStatusOptions` to
  generated `read.findAll` metadata;
- stale metadata/source-map diagnostics before misleading SQL display;
- no silent fallback from stale source maps to transitional
  `mortar:snapshot` markers;
- no Java semantic completion, refactoring engine, or editor-side SQL
  rendering.

R18 IntelliJ scope:

- optional secondary proof that the same source-map/metadata contract can be
  consumed by a thin IntelliJ adapter;
- no IntelliJ-specific contract expansion;
- no broad editor parity program if it distracts from VS Code and core
  reliability.

Source-map-backed behavior is limited to generated fixed reads in R18. Hover
or navigation for arbitrary hand-built DSL `QuerySpec` call sites remains out
of scope unless those calls have a documented query name, source-map entry, and
snapshot/rendered-SQL evidence in a later slice.

### Snapshot And Schema Drift Strategy

R18 should make SQL snapshots a normal developer workflow for the R17 corpus:

- canonical queries keep stable names and snapshot keys;
- SQL assertion failures and snapshot drift must explain whether rendered SQL,
  parameters, parameter types, metadata, or names changed;
- changed column metadata that changes SQL must update snapshot/test
  expectations explicitly;
- snapshot freshness must be checked before editor hover/copy SQL uses a
  snapshot as current evidence.

Schema drift is separate from compile-time refactor safety. It is a tooling
workflow that compares generated metadata against a database schema. R18 should
plan deterministic CLI/LSP diagnostics for the named R17 schema-drift cases,
but it must not describe database schema drift as a Java compile-time guarantee.

### R18 Slices

#### R18.1: Refactor-Safety Fixture Contract

Objective: define supported refactors, unsupported cases, diagnostic
categories, compatibility boundaries, and the exact meaning of
`refactor-safe`.

Expected output: a documented contract for compile-time failures,
tooling-freshness failures, schema-drift diagnostics, and public compatibility
checks over publishable Java APIs plus documented file formats such as
`mortar-metadata-v1` and `mortar-sql-snapshot-v1`.

Non-goals: no product code, no API expansion, no release claim, no binary
compatibility promise for generated `Q*` types across user refactors.

#### R18.2: R17 Refactor Failure Matrix

Objective: turn the R17 handoff into baseline/fail/recover fixture cases.

Expected output: planned negative cases for renamed fields, deleted relations,
deleted metadata inputs, changed column metadata, snapshot drift, and recovery
updates. Evidence should use stable semantic failure categories rather than
full compiler-message snapshots.

Non-goals: no new query families, no fixture-only product helpers, no private
application cases.

#### R18.3: Generated Metadata And Source-Map Contract Hardening

Objective: define the source-map and freshness contract for generated fixed
reads.

Expected output: source-map requirements linking generated read facade symbols,
query IDs, snapshot keys, rendered SQL/snapshot evidence, source locations,
row types, parameters, and freshness diagnostics. If a new metadata/source-map
format is required, add an ADR before implementation.

Non-goals: no arbitrary DSL call-site source maps, no editor-side SQL
rendering, no generated query API expansion.

#### R18.4: Gradle Incremental And Multi-Module Verification

Objective: prove clean and incremental Gradle builds converge across the R17
fixture modules.

Expected output: planned verification for domain/application Mortar-free
boundaries, infrastructure metadata regeneration, stale generated output
detection, and the processor `isolating` classification risk.

Non-goals: no performance claim about incremental build speed, no private app
module migration.

#### R18.5: VS Code Source-Map Hover And Copy SQL Hardening

Objective: make VS Code source-map-backed SQL transparency the primary editor
acceptance path for generated fixed reads.

Expected output: planned LSP/VS Code behavior for hover, copy SQL, stale
metadata diagnostics, and fail-closed behavior against real R17 fixture calls.
IntelliJ may be covered only as a secondary thin-adapter proof of the same
contract.

Non-goals: no broad editor UX, no completion, no IntelliJ parity program, no
marker-only success path for stale metadata.

#### R18.6: Schema Drift, Completion Review, And R19 Handoff

Objective: package the final R18 hardening evidence and decide what remains
for R19.

Expected output: schema-drift workflow over the R17 named cases, final review
against R16/R17 constraints, updated roadmap evidence, and a handoff that keeps
R19 as a go/no-go pre-release-candidate hardening gate rather than a release.

Non-goals: no release, publish, tag, private migration, or performance claim.

### R18 Exit Criteria

R18 can be marked `Done` only when:

- the R17 corpus remains the evidence source for refactor safety, metadata
  freshness, Gradle behavior, schema drift, snapshots, and editor hardening;
- supported rename/delete/changing-column cases have baseline/fail/recover
  evidence;
- generated fixed-read source-map behavior is proven for real R17 fixture
  calls;
- stale metadata/source-map/snapshot data fails closed before editor output;
- clean and incremental Gradle builds converge or fail deterministically across
  the fixture modules;
- domain and application fixture modules remain Mortar-free;
- schema drift remains a separate CLI/tooling workflow;
- no generated API expands beyond R16/R17 decisions;
- VS Code is the primary editor proof and IntelliJ does not expand scope;
- any new cross-tool metadata, source-map, stale-detection, or compatibility
  contract has an accepted ADR;
- final verification and public documentation scrub are recorded in the
  roadmap.

### R18 Risks

- Vague use of `refactor-safe` can hide whether a failure belongs to Java
  compilation, processor diagnostics, tooling freshness, or schema drift.
- Rename/delete tests can become brittle if they assert full compiler output or
  generated-file diffs instead of semantic failure categories.
- Source-map-backed hover can overreach into arbitrary Java DSL call-site
  analysis before generated fixed-read contracts are stable.
- Stale metadata can mislead developers if editor tooling silently falls back
  to old marker-based snapshots.
- Gradle incremental behavior can appear correct in clean builds while stale
  shared metadata remains after incremental changes.
- The existing processor incremental classification may not match the shared
  metadata file behavior and must be verified.
- IntelliJ parity pressure can distract from the primary VS Code/LSP path.
- The R17 fixture can accidentally become a vehicle for product API expansion
  if R18 adds query families for tooling convenience.

### R18 Architecture Debate Outcome

The required xhigh R18 debate concluded that R18 should be a narrow
contract-hardening gate, not a general tooling or editor-feature release.

- `Refactor-safe` means Mortar either regenerates consistent `Q*`, metadata,
  source-map, and snapshot state after supported Java changes, or fails before
  runtime execution. Silent stale success is the failure mode R18 must prevent.
- Compile-time Java failures, tooling freshness failures, and schema-drift
  diagnostics must be documented separately.
- Rename/delete testing should use R17-derived baseline/fail/recover cases and
  stable semantic failure categories, not brittle text rewrites or screenshot
  evidence.
- Source maps should first target generated fixed-read symbols:
  `read.findById` and `read.findAll`. Arbitrary DSL call-site hover/navigation
  is out of scope.
- Stale metadata must fail closed before hover, navigation, or copy SQL. Silent
  fallback to marker-routed snapshots is not acceptable for R18 success.
- Gradle incremental compilation should be judged by convergence and
  deterministic failure, not speed. The processor `isolating` declaration with
  shared metadata output is an explicit R18 risk to verify.
- VS Code is the primary editor gate because it exercises the shared LSP path.
  IntelliJ remains a secondary thin-adapter proof only if it does not expand the
  shared contract.
- New ADRs are required only if R18 changes cross-tool contracts such as
  metadata format, source-map format, stale-detection behavior, or public
  compatibility policy. Slice sequencing alone does not need an ADR.

## Future Maturity Gates

R17 is planned as the real-query coverage gate above. It uses a public
non-application-specific fixture app and query corpus to decide which broader
read-query families should remain explicit DSL, become generated Java-first
ergonomics, require more evidence, or be rejected. It does not authorize
private app migration, generated writes, generated repositories, or release
work.

R18 is planned as the contract-hardening gate above. It should consume the R17
corpus for refactor-safety failures, generated metadata/source-map freshness,
Gradle incremental and multi-module convergence, VS Code source-map-backed SQL
transparency, schema drift workflow, and SQL snapshots as a normal developer
workflow. It does not authorize generated API expansion, release work, or
application migration.

R19 is planned as pre-release candidate hardening. It should run a go/no-go
review for a possible future public beta or release candidate gate, not
automatically release. Its scope includes Windows and Linux CI confidence,
release dry-run, benchmark artifacts from clean commits before claims, upgrade
and migration notes for pre-1.0 changes, security/release/support policy review,
complete CI-running examples, and a go/no-go checklist for a future beta or
later controlled migration pilot.

## Completed Slice: R15 Public API Readiness Hardening

Status: Done

Objective: complete public API readiness hardening before any first release tag
is considered.

Completed scope:

- R15.1 generated `findAll(renderer)` on `Q*` metamodels.
- R15.2 added `MortarNoParameters` and no-parameter generated query overloads.
- R15.3 hardened Spring Boot starter properties, diagnostics, and examples.
- R15.4 expanded PostgreSQL dialect syntax and Testcontainers coverage.
- R15.5 strengthened processor diagnostics and generated-source documentation.
- R15.6 upgraded public usage guidance beyond example snippets.
- R15.7 broadened benchmark scenarios for joins, pages, writes, and tuned
  PgJDBC cases without making unsupported public performance claims.
- R15.8 added a CI-compiling Clean Architecture PostgreSQL example.
- R15.9 finalized the pre-1.0 compatibility and release policy.
- R15.10 completed public Javadocs for handwritten Java APIs and generated
  executor contracts.

Non-goals preserved for release review:

- no release, tag, or Maven Central publication;
- no new dialects;
- no ORM aggregate loading, hidden lazy loading, or broad repository abstraction;
- no Spring dependency in `java/core` or `java/runtime-jdbc`;
- no Rust dependency in the default Java/Spring query runtime.

Final verification record:

- `gradlew.bat check --no-daemon`;
- from `rust`: `cargo fmt --all --check`, `cargo clippy --all-targets --all-features -- -D warnings`, and `cargo test`;
- from `editors/vscode`: `bun run typecheck`;
- public path scrub excluding build outputs and caches;
- `git diff --check`.

Remaining release-review risks:

- Testcontainers-dependent tests may skip locally without Docker and still need
  CI evidence.
- Broad benchmark scenarios must not be described as performance claims until
  retained JMH JSON artifacts exist.
- Generated-source Javadocs need to improve public readability without turning
  generated classes into a larger API surface than necessary.
