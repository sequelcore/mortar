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
- R19: Java Call Resolution And Editor Semantics Hardening.
- R20: Performance And Runtime Efficiency.

Existing application migration waits until Mortar is much more mature. R17 must
prove R16 ergonomics against a realistic public fixture corpus before any such
migration is considered. R19 must harden source-map-backed editor call
resolution before R20 performance work or any future beta, controlled migration
pilot, release-candidate, or public demo claim.

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

Completed R18.1 evidence:

- Added the canonical contract in [`refactor-safety.md`](refactor-safety.md).
- Defined supported R17-derived cases for annotated field rename/deletion,
  relation deletion, relation metadata change, column metadata change, stale
  metadata/source-map/snapshot fail-closed behavior, and schema drift.
- Defined deferred cases, stable diagnostic categories, the
  baseline/fail/recover matrix, and the explicit assertion limits: no brittle
  full `javac` snapshots, no undocumented line-number claims, no
  screenshot-only evidence, and no private application migration proof.
- Research basis: Gradle incremental annotation processing and stale-output
  guidance, Oracle `javac` and annotation processing docs, existing
  compile-test patterns, and the decision to use Mortar's current direct
  `JavaCompiler` harness rather than adding a new test dependency.

#### R18.2: R17 Refactor Failure Matrix

Objective: turn the R17 handoff into baseline/fail/recover fixture cases.

Expected output: planned negative cases for renamed fields, deleted relations,
deleted metadata inputs, changed column metadata, snapshot drift, and recovery
updates. Evidence should use stable semantic failure categories rather than
full compiler-message snapshots.

Non-goals: no new query families, no fixture-only product helpers, no private
application cases.

Completed R18.2 evidence:

- Added
  `java/processor/src/test/java/dev/mortar/processor/MortarRefactorSafetyMatrixTest.java`
  with clean temporary-source baseline/fail/recover compile cases for
  `TicketRecord.summary`, `TechnicianRecord.displayName`,
  `TicketStatusRecord.code`, `TicketRecord.customer`,
  `TicketRecord.assignedTechnician`, `TicketRecord.status`, relation metadata
  validation, and changed column/relation metadata.
- The matrix asserts semantic categories: compile pass/fail, affected
  generated symbol names, stable `MORTAR_PROCESSOR_*` diagnostics, generated
  metadata inventory, and generated SQL inputs. It does not assert full
  compiler output or mutate checked-in R17 fixture sources.
- The unresolved generated-symbol contract was hardened after xhigh
  architecture/reviewer debate and primary-source review. The matrix now keeps
  structured `Diagnostic` data and requires `Diagnostic.Kind.ERROR`, the stale
  `TicketUsage.java` consumer source, the stale symbol token, and independent
  regenerated `Q*` evidence that the producer-side member was renamed or
  removed. It intentionally does not assert javac `Diagnostic.getCode()` values,
  full localized messages, exact diagnostic counts, ordering, or line/column
  numbers.
- Extended Rust schema-drift coverage in
  `rust/crates/mortar-compiler/src/lib.rs` for the R17 handoff cases
  `tickets.customer_id`, `tickets.assigned_technician_id`,
  `tickets.status_code`, and `technicians.region`.
- Added an infrastructure SQL drift fixture in
  `examples/query-corpus-infrastructure-postgres/src/test/java/dev/mortar/examples/querycorpus/postgres/PostgresTicketReaderTest.java`
  proving that the baseline generated `TicketReader.findHeader` SQL matches
  the current snapshot expectation, and a changed `TicketRecord.summary`
  column-metadata variant no longer matches that stale expectation until the
  updated SQL expectation is asserted.
- Focused verification passed on 2026-06-02:
  `gradlew.bat :java:processor:test --tests
  dev.mortar.processor.MortarRefactorSafetyMatrixTest --no-daemon`;
  `gradlew.bat :examples:query-corpus-infrastructure-postgres:test --tests
  dev.mortar.examples.querycorpus.postgres.PostgresTicketReaderTest.changedColumnMetadataIsASemanticSqlSnapshotDriftCase --no-daemon`;
  and
  `cd rust && cargo test
  detects_r17_schema_drift_cases_from_ticket_fixture_metadata`.
- R18.2 intentionally does not claim Gradle incremental convergence,
  source-map locations/freshness, VS Code source-map behavior, or full
  schema-drift workflow completion. Those remain R18.3, R18.4, R18.5, and
  R18.6 work.
- Final R18.1/R18.2 verification passed on 2026-06-02:
  `gradlew.bat check --no-daemon`; from `rust`,
  `cargo fmt --all --check`,
  `cargo clippy --all-targets --all-features -- -D warnings`, and
  `cargo test`; from `editors/vscode`, `bun run typecheck`;
  `git diff --check`; and the private path/private project scrub excluding
  build outputs and caches.

#### R18.3: Generated Metadata And Source-Map Contract Hardening

Objective: define the source-map and freshness contract for generated fixed
reads.

Expected output: source-map requirements linking generated read facade symbols,
query IDs, snapshot keys, rendered SQL/snapshot evidence, source locations,
row types, parameters, and freshness diagnostics. If a new metadata/source-map
format is required, add an ADR before implementation.

Non-goals: no arbitrary DSL call-site source maps, no editor-side SQL
rendering, no generated query API expansion.

R18.3 was reviewed after the R18.2 unresolved-symbol cleanup. The existing
`mortar-metadata-v1` query fields already link fixed reads to query IDs,
generated source owner/member/type, parameter metadata, row types, and snapshot
keys, but they do not yet define source locations or freshness diagnostics.
R18.3 therefore adds ADR-0007 before implementation. The accepted design keeps
`META-INF/mortar/entities.json` as `mortar-metadata-v1` and adds a sibling
`META-INF/mortar/source-map.json` artifact with format `mortar-source-map-v1`.
The new artifact is keyed by the existing fixed-read query IDs and records
generated entity/read/member coordinates, query name, snapshot key, row type,
ordered parameters, a stable source anchor, and deterministic freshness
fingerprints. It does not store absolute paths, timestamps, editor commands,
repository call-site mappings, rendered SQL, or Java line/column locations.

Research conclusions recorded for R18.3/R18.4:

- Gradle incremental annotation processing distinguishes isolating processors
  from aggregating processors. A processor that writes shared outputs derived
  from multiple annotated types fits the aggregating model unless the shared
  output is split or produced by a separate aggregation step:
  https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing
- Gradle incremental builds compare task inputs and outputs, while stale-output
  cleanup is limited; R18.4 must therefore prove stale metadata/source-map
  output cannot remain apparently valid after supported source changes:
  https://docs.gradle.org/current/userguide/incremental_build.html
- Java `Filer` originating elements are dependency-management hints at
  compilation-unit granularity, not a portable method-level source-position
  contract:
  https://docs.oracle.com/en/java/javase/21/docs/api/java.compiler/javax/annotation/processing/Filer.html
- Java `Messager` locations can be element/annotation/value hints, but the API
  allows them to be unavailable or approximate:
  https://docs.oracle.com/en/java/javase/22/docs/api/java.compiler/javax/annotation/processing/Messager.html
- ECMA-426 source maps support the general generated-to-original mapping
  concept, but Mortar does not adopt the JavaScript VLQ mapping format for
  generated Java fixed reads:
  https://ecma-international.org/publications-and-standards/standards/ecma-426/
- LSP hover/definition use document positions, ranges, and locations, but R18.3
  treats those as future consumer inputs only:
  https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/

#### R18.4: Gradle Incremental And Multi-Module Verification

Objective: prove clean and incremental Gradle builds converge across the R17
fixture modules.

Expected output: planned verification for domain/application Mortar-free
boundaries, infrastructure metadata regeneration, stale generated output
detection, and the processor incremental classification risk.

R18.4 changes the processor descriptor from `isolating` to `aggregating`
because `entities.json` and `source-map.json` are shared resources. Verification
must use disposable temporary fixture projects or temporary source workspaces,
not checked-in source mutation. The evidence shape is semantic inventory:
generated symbol presence/absence, query IDs, source-map anchors, freshness
fingerprints, and clean-vs-incremental convergence for the same source state.
Whole generated-file snapshots, raw javac message snapshots, exact diagnostic
counts/order, line/column assertions, and editor behavior are out of scope.

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

Implementation plan:

1. Add source-map-backed Rust LSP lookup for generated fixed-read calls only.
   The LSP loads `META-INF/mortar/entities.json`,
   `META-INF/mortar/source-map.json`, and `mortar.sql.snap.json` from the
   active workspace root, validates `mortar-source-map-v1` freshness against
   `mortar-metadata-v1`, resolves canonical generated read-facade calls such as
   `QClient.CLIENT.read(renderer).findById(id)` and
   `QClient.CLIENT.read(renderer).findAll()` by metamodel context plus
   `read.findById` / `read.findAll`, and then uses the snapshot key for SQL.
2. Keep explicit `mortar:snapshot` marker routing as a manual legacy path only.
   If the caret is on a generated fixed-read call and metadata/source-map data
   is missing or stale, hover, copy SQL, and navigation must return no editor
   output instead of falling back to the marker path.
3. Add minimal LSP definition support that navigates to the matching snapshot
   entry in `mortar.sql.snap.json`. R18.5 does not navigate to arbitrary Java
   DSL locations or generated-source line/column positions because ADR-0007
   intentionally records stable anchors instead of compiler line locations.
4. Update the VS Code smoke fixture and tests to prove hover, copy SQL, and
   definition use source-map-backed generated fixed-read calls.

Verification plan:

- Rust LSP unit tests cover fresh source-map hover/actions/definition, stale
  source-map fail-closed behavior, and the legacy marker path.
- VS Code smoke tests exercise `vscode.executeHoverProvider`,
  `vscode.executeCodeActionProvider`, and `vscode.executeDefinitionProvider`
  against the source-map-backed fixture.
- Full R18 closure runs the required Java, Rust, VS Code, publish dry-run,
  whitespace, and private-path scrub gates.

Completed R18.5 evidence:

- The xhigh debate selected a strict layered hybrid: source-map plus fresh
  metadata is authoritative for generated fixed-read editor routing; snapshots
  remain SQL evidence; explicit markers remain only as a legacy/manual path and
  never mask stale source-map data.
- `rust/crates/mortar-lsp/src/lib.rs` now consumes `mortar-source-map-v1` and
  `mortar-metadata-v1` for canonical generated read-facade calls, matches
  source-map entries by generated metamodel/read namespace context plus
  generated member instead of `generated_member` alone, fails closed on
  stale/missing/ambiguous/unparseable source-map evidence, publishes stale
  source-map diagnostics, and navigates definitions to the matching snapshot
  entry.
- `editors/vscode` remains a thin language-client adapter. Its test workspace
  now carries source-map and metadata artifacts, and smoke tests cover hover,
  copy/EXPLAIN code actions, and definition through VS Code provider commands.

#### R18.6: Schema Drift, Completion Review, And R19 Handoff

Objective: package the final R18 hardening evidence and decide what remains
for R19.

Expected output: schema-drift workflow over the R17 named cases, final review
against R16/R17 constraints, updated roadmap evidence, and a handoff that keeps
R19 focused on Java call-resolution and editor-semantics hardening rather than
a release.

Non-goals: no release, publish, tag, private migration, or performance claim.

Completed R18.6 evidence:

- Documentation closure updated `docs/lsp.md`, `docs/metadata.md`,
  `docs/refactor-safety.md`, `docs/roadmap.md`, and this plan to describe the
  source-map-backed editor contract, fail-closed behavior, and legacy marker
  limits.
- R19 handoff identifies Java call-resolution and editor-semantics hardening as
  the next risk: R18's lightweight Java matcher supports canonical generated
  fixed-read calls, but not local aliases, helper-returned receivers, or
  arbitrary Java call semantics.
- R20 remains the performance phase. Performance work should gather repeated
  clean-commit JMH/PostgreSQL artifacts, Windows/Linux CI confidence, release
  dry-run evidence, upgrade notes, and a go/no-go checklist before any public
  claim or beta/release-candidate decision.
- R18.6 does not implement R19, does not add Java public API, and does not
  authorize release, push, tag, publication, private migration, or broad editor
  UX work.

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

### R18.3/R18.4 Architecture Debate Outcome

The required xhigh R18.3/R18.4 debate concluded on 2026-06-02:

- R18.3 requires an ADR because source-map/freshness data is a cross-tool
  contract consumed by Java processor output and Rust tooling.
- Source-map/freshness data should live in a separate
  `mortar-source-map-v1` artifact instead of adding required fields to
  `mortar-metadata-v1`.
- The current processor classification as `isolating` is not defensible while
  it writes one shared metadata inventory. R18.4 should classify the processor
  as `aggregating` unless a future slice splits outputs per entity.
- Reclassification alone is insufficient; tests must prove stale shared
  metadata/source-map output is overwritten or fails closed when entities or
  fixed-read query entries disappear.
- Because shared artifacts must also refresh when all annotated entities are
  removed, the processor uses non-claiming wildcard annotation support and
  writes shared artifacts in the final processing round. It must not claim
  unrelated annotations or skip Mortar entities generated in later rounds.
- Clean and incremental convergence must be proven in temporary fixture copies
  or generated test projects, never by destructive mutation of checked-in R17
  sources.
- Verification should parse semantic inventories and freshness fingerprints,
  not compare full generated files.
- R18.3/R18.4 stop at contract, parser readiness, and Gradle convergence. VS
  Code hover/copy SQL remains R18.5.

### R18.1/R18.2 Architecture Debate Outcome

The required xhigh R18.1/R18.2 debate approved executable evidence now, but
only for deterministic clean compile and semantic SQL/schema cases.

- R18.2 should implement compile-fail tests for R17-shaped field
  rename/deletion and relation deletion because temporary source workspaces can
  model baseline/fail/recover without mutating repository sources.
- R18.2 should not claim generated metadata freshness, source-map freshness, or
  Gradle incremental correctness. The existing processor `isolating`
  declaration remains good enough for clean compile tests, but shared
  `META-INF/mortar/entities.json` output is an R18.4 convergence risk.
- Compiler assertions should match semantic categories: compile failure plus
  affected generated symbol, stable Mortar processor diagnostic code, or named
  SQL/snapshot metadata semantics. Full compiler-message snapshots remain
  forbidden.
- Changed column metadata should be tested as SQL snapshot drift now when it
  changes rendered SQL or query metadata for a named R17 query. External
  database schema drift remains a separate tooling workflow.
- No new ADR is required for R18.1/R18.2 because they do not change a
  cross-tool metadata format, source-map format, stale-detection behavior,
  public API, or official processor incremental classification.

## R19 Canonical Design: Java Call Resolution And Editor Semantics Hardening

Status: Done

R19 hardened Java call resolution and editor semantics for generated fixed-read
calls. R19.1, R19.2, R19.2a, R19.3, R19.4, and R19.5 are implemented,
documented, reviewed, and verified.

### Problem Statement

R18.5 made VS Code/LSP source-map-backed hover, copy SQL, PostgreSQL EXPLAIN
code actions, snapshot-entry definition, stale-source-map diagnostics, and
snapshot-definition routing work for canonical generated read calls:

```java
QClient.CLIENT.read(renderer).findById(id);
QClient.CLIENT.read(renderer).findAll();
CLIENT.read(renderer).findById(id);
```

R18 deliberately used lightweight Java matching. That was appropriate for a
contract-hardening gate because fresh `mortar-source-map-v1`,
`mortar-metadata-v1`, and `mortar.sql.snap.json` were the primary trust
boundary. The remaining risk is that developers naturally introduce local
aliases around generated metamodels or read namespaces, while arbitrary helper
methods, reassignment, shadowing, wildcard imports, and Java type inference can
make a token-only resolver either miss valid code or, worse, route editor SQL
to the wrong snapshot.

R19 exists to harden that boundary before R20 performance work and before any
public demo claim.

### Decision

R19 adopts the ADR-0008 bounded hybrid:

- keep `mortar-source-map-v1` plus fresh `mortar-metadata-v1` authoritative;
- move call recovery toward local syntax-aware Java resolution in the Rust LSP;
- broaden support only to explicit same-file local alias patterns that do not
  require Java type binding;
- keep hover, copy SQL, EXPLAIN, definition, and diagnostics driven by one
  resolver result;
- fail closed for unsupported generated-looking calls rather than guessing.

`tree-sitter-java` is the preferred implementation candidate because it fits
the Rust tooling path and provides local syntax structure. R19 planning does
not claim a full Java parser, full Java semantic resolution, classpath-aware
binding, or helper-returned receiver support.

### R19 Slices

R19 residual hardening before alias expansion.

Status: Done.

The required xhigh debate for the residual-risk slice concluded on
2026-06-02 that this slice should not implement alias success. The safe scope
is hardening only:

- add integration coverage for malformed and transitional Java buffers during
  full-document open/change handling;
- expand canonical receiver coverage for deeply parenthesized and cast-wrapped
  generated receivers and read namespaces;
- add negative lexical-scope coverage for lambda, catch, and switch blocks so
  generated-looking aliases do not leak into unrelated calls;
- keep local alias and read-namespace alias success deferred until a later
  slice introduces an identity-bearing local binding model;
- make fail-closed diagnostics truthful for unsupported generated-looking
  syntax while preserving stale or missing source-map diagnostics for source
  evidence failures.

Changed files for this bounded slice are expected to stay within
`rust/crates/mortar-lsp/src/lib.rs`, `docs/plan.md`, `docs/roadmap.md`, and
`docs/lsp.md`. Verification is the full Java, Rust, VS Code typecheck, diff,
scrub, and reviewer gate requested for R19 residual hardening.

Completed scope:

- malformed and transitional full-document Java buffers fail closed during LSP
  open/change handling and recover when the buffer becomes syntactically
  complete;
- deeply parenthesized and cast-wrapped canonical generated receivers and read
  namespaces still resolve through source-map-backed snapshot routing;
- lambda, catch, and switch local alias calls remain unsupported and fail
  closed with an unsupported-syntax diagnostic;
- local alias success, read-namespace alias success, helper-returned receivers,
  wildcard static imports, and Java semantic binding remain deferred.

Verification passed on 2026-06-02:

```bash
gradlew.bat check --no-daemon
cd rust && cargo fmt --all --check
cd rust && cargo clippy --all-targets --all-features -- -D warnings
cd rust && cargo test
cd editors/vscode && bun run typecheck
git diff --check
```

Diff-level private path/project scrub had zero matches. The broad scrub
excluding build, cache, dependency, and generated outputs found only pre-existing
Java test fixture data outside this slice. Reviewer gates found no remaining
ADR-0008, source-map authority, fail-closed, Java API, private-path, or public
overclaiming blockers after documentation wording was narrowed.

R19.1: Call-resolution contract and ADR/update.

Status: Done.

- Owns ADR-0008, this plan, and roadmap sequencing.
- Defines supported and unsupported call-resolution patterns before
  implementation.
- Verification: docs review for R18 scope accuracy, ADR-0007 compatibility,
  no implementation claims, and no fake parser promises.

R19.2: Local Java syntax resolution foundation.

Status: Done.

- Owns Rust LSP resolver structure only.
- Preserves R18 canonical direct-call behavior before adding aliases.
- Evaluates the parser dependency and parser boundary with tests.
- Verification: Rust resolver matrix for R18 direct calls, explicit static
  imports, stale source maps, missing metadata, and ambiguous source-map
  entries.

R19.3: Canonical aliases, local variables, and static import resolution.

Status: Done.

- Supports only same-file local aliases assigned once to a direct generated
  metamodel constant or to a direct generated `.read(renderer)` namespace.
- Keeps explicit static imports supported; wildcard static imports are not a
  success path.
- Rejects type-only aliases, alias chains, helper-returned receivers,
  parenthesized alias initializers, reassignment, ambiguous conditional
  aliases, lambda/catch/switch alias scopes, field aliases, ambiguous explicit
  import collisions, wildcard static imports, and aliases whose identity is not
  proven locally from same-file syntax.
- Verification: Rust LSP tests cover positive metamodel aliases, read
  namespace aliases, hover/copy SQL/EXPLAIN/definition parity, explicit local
  declaration requirements, reassignment, ambiguity, scope boundaries, and
  stale or missing source-map fail-closed behavior.

R19.4: Fail-closed diagnostics and VS Code smoke fixture expansion.

Status: Done.

- Ensures hover, copy SQL, EXPLAIN, definition, and diagnostics agree on the
  same resolver outcome.
- Adds fail-closed reason codes for unsupported alias syntax, ambiguous alias,
  reassigned alias, stale or missing source-map evidence, missing SQL snapshot,
  and malformed Java buffer.
- Expands VS Code smoke fixtures for canonical success, supported local alias
  success, unsupported alias diagnostics, copy SQL, EXPLAIN, and snapshot
  definition routing.
- Focused verification passed on 2026-06-02: `cd rust && cargo test -p
  mortar-lsp`; from `editors/vscode`, `bun run typecheck` and `bun run test`
  with the PostgreSQL EXPLAIN datasource smoke pending because no
  `MORTAR_VSCODE_EXPLAIN_CONNECTION` was configured.

R19.5: Editor semantics review and R20 performance handoff.

Status: Done.

- Reviewed editor behavior against LSP semantics and public docs.
- Aligned `docs/lsp.md`, `docs/metadata.md`, this plan, and the roadmap after
  review evidence existed. `docs/vscode.md` was reviewed and did not need R19
  wording changes.
- Hands off to R20 with performance risks separated from editor correctness.
- Verification: full Java, Rust, VS Code, diff, scrub, and review gates passed
  on 2026-06-02 before the R19 completion claim.

### Non-Goals

- no runtime Java API changes unless explicitly justified by a later slice;
- no generated Java API expansion;
- no performance implementation or performance claim;
- no AI/agent-friendly phase;
- no full IDE replacement;
- no full Java semantic engine or classpath-aware type binding;
- no helper-returned receiver support;
- no arbitrary DSL call-site hover/navigation;
- no editor-side SQL rendering;
- no public release, release candidate, tag, publication, private migration, or
  public demo claim.

### Research Basis

- LSP 3.17 documents hover, code action, definition, and diagnostics as
  separate language features. A server can return no hover, no definition, or
  no code action when it cannot resolve trustworthy data, and publish
  diagnostics separately.
- VS Code language-server guidance says language analysis can parse large
  amounts of code and should run in a separate process to avoid extension-host
  performance cost.
- Tree-sitter provides parser infrastructure and Rust bindings; the
  `tree-sitter-java` crate provides Java syntax support but not Java type
  binding.
- Java `JavaCompiler` and `Trees` can expose compiler diagnostics and syntax
  trees, but deeper use would require Java process and classpath integration.
- Eclipse JDT LS demonstrates the scale of a full Java language server,
  including Gradle/Maven support, diagnostics, completion, navigation, and
  annotation-processing integration.
- JavaParser and JavaSymbolSolver demonstrate AST plus symbol-solving options,
  but add Java dependency and workspace/classpath complexity that R19 avoids.

### Architecture Debate Outcome

The required xhigh debate concluded:

- R19 should use a bounded hybrid: source-map freshness remains authoritative,
  while Java call recovery becomes local syntax-aware resolution.
- Continuing to broaden token matching was rejected because alias, scope,
  shadowing, and reassignment handling would become an ad hoc parser.
- Full `javac`, Eclipse JDT, or JavaParser symbol-solving integration was
  rejected for R19 because the Java process, classpath, and workspace-model
  burden exceeds the hardening goal.
- Lexical alias heuristics without syntax structure were rejected because local
  aliases need initializer and scope information.
- R19 must describe itself as syntax-aware local resolution, not full Java call
  resolution.
- Returning no editor result for valid but unsupported Java is acceptable;
  returning stale or wrong SQL is not.

### Exit Criteria

R19 can be marked `Done` only when:

- R18 canonical generated fixed-read calls still work;
- supported alias patterns are covered by positive resolver and VS Code smoke
  tests;
- unsupported generated-looking calls fail closed with diagnostics where
  appropriate;
- hover, copy SQL, EXPLAIN, definition, and diagnostics share one resolver
  outcome;
- docs clearly list unsupported patterns;
- no public docs claim full Java parser support, full Java semantics, runtime
  API changes, performance implementation, release, or migration;
- full verification and review evidence is recorded in the roadmap.

### R19.1/R19.2 Completion Evidence

R19.1 added executable Rust LSP tests for the call-resolution boundary before
the parser refactor:

- R18 direct generated calls still resolve.
- R18 explicit static imports still resolve.
- `findAll` still resolves.
- multiple source-map entries with the same read member still disambiguate by
  generated metamodel context;
- stale, missing, duplicate, or invalid source-map/snapshot evidence still
  fails closed;
- generated-looking helper-returned receivers fail closed;
- wildcard static imports are not a success path;
- local metamodel aliases, local read-namespace aliases, parenthesized aliases,
  reassigned aliases, and same-name aliases in unrelated methods are not
  overclaimed before R19.3;
- same-name generated-looking aliases in unrelated lexical scopes do not poison
  ordinary receivers;
- ordinary non-Mortar `read*` APIs are not treated as generated-looking calls;
- ambiguous bare receivers are not inferred;
- harmless parentheses around canonical receivers/read namespaces still resolve;
- generated-looking text inside Java strings and comments is ignored;
- UTF-16 LSP positions remain correct.

R19.2 added a local Java syntax-resolution foundation in
`rust/crates/mortar-lsp` using `tree-sitter-java`. The resolver now parses the
active Java document, extracts import declarations, target method invocations,
the immediate `.read(renderer)` receiver chain, and byte ranges, then maps the
syntax result back into the existing resolver outcome:

- `Call` for supported canonical generated fixed-read calls;
- `FailClosed` for generated-looking unsupported or untrusted calls;
- `NotGeneratedCall` for ordinary Java code.

The implementation keeps fresh `mortar-metadata-v1` plus
`mortar-source-map-v1` authoritative and keeps `mortar.sql.snap.json` as the
only SQL evidence source. It does not add Java type binding, classpath-aware
semantics, alias success, helper-returned receiver support, wildcard static
import success, Java public API changes, generated Java API changes, VS Code
client changes, performance work, release work, or R20 behavior.

Focused verification passed on 2026-06-02:

```bash
cd rust
cargo test -p mortar-lsp
```

### R19.3/R19.4 Completion Evidence

The required xhigh architecture debate for R19.3/R19.4 concluded on
2026-06-02 that the narrowest safe alias-success model is an identity-bearing
local binding model, not a generated-looking heuristic:

- keep direct canonical calls and explicit static-import constants supported;
- support `var client = QClient.CLIENT; client.read(renderer).findById(id)`;
- support `var read = QClient.CLIENT.read(renderer); read.findById(id)`;
- reject full Java semantic/type/classpath resolution, helper-returned
  receivers, wildcard static imports, alias chains, type-only aliases,
  reassigned aliases, ambiguous aliases, ambiguous explicit import collisions,
  and aliases whose identity cannot be proven from same-file syntax.

R19.3 implements that bounded model in `rust/crates/mortar-lsp`. The local
syntax resolver records exact local variable initializers for direct generated
metamodel constants and direct generated read namespaces, then routes hover,
copy SQL, PostgreSQL EXPLAIN, and definition through the existing
source-map-backed snapshot path. Fresh `mortar-metadata-v1` plus
`mortar-source-map-v1` remains authoritative, and marker snapshots are not used
as a fallback for valid generated alias syntax when source-map evidence is
stale or missing.

R19.4 adds reason-specific fail-closed diagnostics and smoke fixtures:

- `mortar-alias-unsupported`;
- `mortar-alias-ambiguous`;
- `mortar-alias-reassigned`;
- `mortar-source-map-stale`;
- `mortar-snapshot-missing`;
- `mortar-java-buffer-malformed`.

Changed files are limited to Rust LSP behavior/tests, VS Code smoke
fixtures/tests, and public R19 documentation. No Java runtime API, generated
Java API, VS Code business logic, helper-returned receiver support, wildcard
static import success, Java type binding, classpath-aware semantics,
performance work, release work, migration work, or R20 behavior changed.

Focused verification passed on 2026-06-02:

```bash
cd rust
cargo test -p mortar-lsp
cd ../editors/vscode
bun run typecheck
bun run test
```

### R19.5 Completion Evidence

The required xhigh architecture review for R19.5 concluded on 2026-06-02:

- hover, copy SQL, PostgreSQL EXPLAIN, definition, and diagnostics share the
  same source-map-backed resolver outcome for generated fixed-read calls;
- supported alias shapes are documented exactly as the two R19.3 same-file local
  binding forms and are not overclaimed;
- unsupported alias shapes fail closed with reason-specific diagnostics;
- marker fallback is impossible for stale or missing generated source-map
  evidence because generated calls return `FailClosed` before the legacy marker
  path can run;
- the only closure blockers were stale `docs/metadata.md` wording and a
  synthetic username-bearing URI in a Rust test; both were fixed in R19.5.

No resolver code gap was found, and no new semantic test was added. Existing
Rust LSP tests already assert supported alias parity across hover, copy SQL,
EXPLAIN, definition, and diagnostics, plus stale and missing source-map
fail-closed behavior without marker fallback. Existing VS Code smoke tests cover
canonical generated reads, supported metamodel aliases, supported read-namespace
aliases, unsupported alias diagnostics, copy SQL, EXPLAIN command contribution,
and snapshot definition routing.

Changed modules/docs: `rust/crates/mortar-lsp`, `docs/lsp.md`,
`docs/metadata.md`, `docs/plan.md`, and `docs/roadmap.md`. No Java runtime API,
generated Java API, metadata format, source-map format, VS Code command
contract, performance behavior, release, publication, migration, or R20
implementation changed.

Verification passed on 2026-06-02:

```bash
gradlew.bat check --no-daemon
cd rust && cargo fmt --all --check
cd rust && cargo clippy --all-targets --all-features -- -D warnings
cd rust && cargo test
cd editors/vscode && bun run typecheck
cd editors/vscode && bun run test
git diff --check
```

`bun run test` passed with six VS Code smoke tests and one pending
datasource-backed EXPLAIN smoke because `MORTAR_VSCODE_EXPLAIN_CONNECTION` was
not configured. Private path/project scrub excluding build, cache, dependency,
and generated outputs had zero matches after the synthetic test URI cleanup.

R20 performance handoff:

- keep Java/Rust performance measurement boundaries explicit: Java owns the
  runtime query path and JMH/PostgreSQL execution evidence; Rust owns tooling
  and LSP measurement only;
- make benchmark reproducibility the first deliverable, with retained repeated
  clean-commit artifacts before public claims;
- profile allocation and latency for generated fixed reads, DSL reads, and JDBC
  execution paths;
- measure generated query path overhead separately from ordinary and tuned JDBC
  baselines;
- measure LSP resolver performance for parser/resolver latency, memory
  allocation, cache behavior, and large-document behavior without changing R19
  semantics;
- make no public performance claims without retained evidence and reviewer
  sign-off.

## R20 Canonical Plan: Performance And Runtime Efficiency

Status: Done.

R20 is measurement and planning first. It closes as a completed measurement and
decision-gate phase. R20 does not authorize optimization, runtime API changes,
editor semantic changes, publication, migration, release work, or public
performance claims.

### R20.1 Benchmark Readiness Audit And Research

Status: Done.

Output:

- `docs/benchmarks/r20-benchmark-readiness.md` records source-backed benchmark
  findings, the xhigh debate outcome, current benchmark inventory, trust
  classification, environment metadata requirements, repeatability rules,
  comparison rules, retained artifact policy, and public claim policy.
- Existing benchmark reports remain internal engineering baselines. Local build
  outputs and controlled JDBC-double results are not public-ready evidence.

Acceptance criteria:

- controlled JDBC doubles are blocked from supporting database claims;
- SQL rendering, fake JDBC adapter overhead, live PostgreSQL execution, and
  Rust LSP/tooling performance are separate categories;
- ordinary JDBC, tuned PgJDBC, reusable prepared JDBC, maximum handwritten JDBC,
  jOOQ, and QueryDSL SQL comparisons are explicitly named and constrained;
- no public claim is allowed without retained raw artifacts and review.

### R20.2 Canonical Benchmark Plan

Status: Done.

Output:

- `docs/roadmap.md`, this plan, `docs/performance.md`, and
  `docs/benchmarks/README.md` now point to the R20 benchmark-readiness audit.
- R20 is split into evidence-gated slices before optimization candidates are
  ranked.

Canonical slices:

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

### R20.3 Java Runtime Baseline Matrix

Status: Done.

R20.3 configures retained artifact generation for the Java runtime
JMH/PostgreSQL baseline matrix. The manual `Benchmarks` workflow now defaults
to the canonical fixed-read include regex, requires at least two repeated runs,
and uploads a retained bundle under
`java/benchmarks/build/reports/jmh/r20.3` with raw JMH JSON, `manifest.json`,
`commands.txt`, `summary.md`, `review-notes.md`, and environment files.

The R20.3 matrix rows are ordinary JDBC, reusable prepared JDBC, ordinary JDBC
`findById`, reusable prepared JDBC `findById`, tuned PgJDBC reusable JDBC,
Mortar render-per-call, Mortar pre-rendered SQL, Mortar processor-generated
executor, Mortar prepared processor-generated executor, Mortar tuned
processor-generated executor, jOOQ reference, and QueryDSL SQL reference.
jOOQ and QueryDSL are reference-library rows only.

R20.3 explicitly excludes optional variants, handwritten generated-style Mortar
rows, join/page rows, update-batch rows, and controlled fake-JDBC rows from the
baseline matrix. Those move to R20.4 or R20.5. R20.3 makes no public
performance claim, changes no runtime code, and changes no Java public API.

Verification passed on 2026-06-02:

- `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`;
- `gradlew.bat verifyBenchmarkWorkflow --no-daemon`;
- focused live PostgreSQL JMH smoke generated
  `java/benchmarks/build/reports/jmh/r20.3-smoke.json` as build output;
- `gradlew.bat check --no-daemon`;
- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- private path/project scrub excluding build, cache, dependency, and generated
  outputs.

### R20.4 Generated Fixed-Read Profiling

Status: Done.

R20.4 profiles generated `findById` fixed-read overhead against matched JDBC
baselines without changing runtime behavior. The required xhigh debate
concluded that R20.4 must isolate six live PostgreSQL/Testcontainers rows:
ordinary JDBC `findById`, reusable prepared JDBC `findById`, tuned PgJDBC
reusable JDBC `findById`, Mortar processor-generated `findById`, Mortar
prepared processor-generated `findById`, and Mortar tuned processor-generated
`findById`.

`java/benchmarks` now exposes focused R20.4 JMH presets for throughput,
allocation, and sample-time latency:

```bash
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedRead --no-daemon
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhR20GeneratedFixedReadLatency --no-daemon
```

Canonical include regex:

```text
PostgresExecutionBenchmark\.(plainJdbcFindByIdFetch|plainJdbcReusableFindByIdFetch|plainJdbcTunedReusableFindByIdFetch|mortarProcessorGeneratedFindByIdFetch|mortarPreparedProcessorGeneratedFindByIdFetch|mortarTunedProcessorGeneratedFindByIdFetch)$
```

Optional variants, handwritten generated-style Mortar rows, join/page rows,
update-batch rows, jOOQ, QueryDSL SQL, and controlled fake-JDBC rows are
excluded from R20.4 interpretation. They remain available benchmark methods,
but R20.4 does not use them for generated fixed-read overhead conclusions.

R20.4 records local/internal evidence only. Local smoke JSON under
`java/benchmarks/build` proves task and regex wiring but is not public
performance evidence and must not be committed. Retained local or CI artifacts
still need raw JSON, manifest, commands, environment metadata, dataset notes,
limitations, review notes, and repeated runs before any optimization candidate
is proposed.

R20.4 `Done` means the generated fixed-read profiling harness, grouping guard,
local instructions, and local smoke proof are complete. It does not mean the
retained evidence gate for optimization proposals or public performance
reporting is satisfied.

Changed files/docs: `java/benchmarks/build.gradle.kts`,
`java/benchmarks/src/test/java/dev/mortar/benchmarks/PostgresExecutionBenchmarkTest.java`,
`docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
`docs/performance.md`, this plan, and `docs/roadmap.md`.

Migration note: no Java public API, runtime behavior, generated Java API, Rust
tooling behavior, benchmark threshold, release, publication, migration,
optimization, or public performance claim changed.

Verification passed on 2026-06-02:

- `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`;
- focused R20.4 live PostgreSQL JMH smoke generated
  `java/benchmarks/build/reports/jmh/r20.4-generated-fixed-read-smoke.json` as
  build output;
- `gradlew.bat check --no-daemon`;
- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- private path/project scrub excluding build, cache, dependency, and generated
  outputs.

### R20.5 DSL Render/Execute Profiling

Status: Done.

R20.5 profiles existing dynamic DSL render/execute paths over live
PostgreSQL/Testcontainers rows without changing runtime behavior. The required
xhigh debate concluded that R20.5 should include all three existing broader DSL
measurement categories, but only through current benchmark rows:

- simple read render/execute context: ordinary JDBC simple read, Mortar DSL
  render-per-call simple read, and Mortar pre-rendered simple read;
- joined ordered paginated read: reusable prepared JDBC join/page and Mortar
  DSL join/page;
- write batch: reusable prepared JDBC update batch and Mortar DSL update batch.

`java/benchmarks` now exposes focused R20.5 JMH presets for throughput,
allocation, and sample-time latency:

```bash
gradlew.bat :java:benchmarks:jmhR20DslShapes --no-daemon
gradlew.bat :java:benchmarks:jmhR20DslShapesAllocation --no-daemon
gradlew.bat :java:benchmarks:jmhR20DslShapesLatency --no-daemon
```

Canonical include regex:

```text
PostgresExecutionBenchmark\.(plainJdbcFetch|mortarJdbcFetch|mortarPreRenderedJdbcFetch|plainJdbcJoinPageFetch|mortarJoinPageFetch|plainJdbcUpdateBatch|mortarUpdateBatch)$
```

R20.5 excludes `plainJdbcReusableStatementFetch`, generated fixed-read R20.4
rows, optional variants, handwritten generated-style Mortar rows, jOOQ,
QueryDSL SQL, rendering-only microbenchmarks, and controlled fake-JDBC rows
from its interpretation boundary. jOOQ and QueryDSL remain out because R20.5
does not define broader-shape fair-comparison boundaries for those libraries.

R20.5 records local/internal evidence only. Local smoke JSON under
`java/benchmarks/build` proves task and regex wiring but is not public
performance evidence and must not be committed. Retained local or CI artifacts
still need raw JSON, manifest, commands, environment metadata, dataset notes,
limitations, review notes, and at least two repeated runs per selected profile
before any optimization candidate is proposed.

R20.5 `Done` means the DSL profiling harness, grouping guard, local
instructions, and local smoke proof are complete. It does not mean the retained
evidence gate for optimization proposals or public performance reporting is
satisfied.

Changed files/docs: `java/benchmarks/build.gradle.kts`,
`java/benchmarks/src/test/java/dev/mortar/benchmarks/PostgresExecutionBenchmarkTest.java`,
`docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
`docs/performance.md`, this plan, and `docs/roadmap.md`.

Migration note: no Java public API, runtime behavior, generated Java API, Rust
tooling behavior, benchmark threshold, release, publication, migration,
optimization, or public performance claim changed.

Verification passed on 2026-06-02:

- `gradlew.bat :java:benchmarks:test --tests dev.mortar.benchmarks.PostgresExecutionBenchmarkTest --no-daemon`;
- focused R20.5 live PostgreSQL JMH smoke generated
  `java/benchmarks/build/reports/jmh/r20.5-dsl-shapes-smoke.json` as build
  output;
- `gradlew.bat check --no-daemon`;
- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- private path/project scrub excluding build, cache, dependency, and generated
  outputs.

### R20.6 Rust LSP Resolver Benchmarking

Status: Done.

R20.6 adds an internal Criterion benchmark harness for current Rust LSP
tooling/editor behavior without changing R19 semantics. The required xhigh
performance debate concluded that R20.6 must measure the existing
source-map-backed resolver path only, keep Rust tooling metrics separate from
Java runtime metrics, and block optimization candidates until repeated retained
artifacts plus profiler/allocation evidence isolate a dominant cost.

Harness:

```bash
cd rust
cargo bench -p mortar-lsp --bench r20_lsp_resolver
```

Smoke:

```bash
cd rust
cargo bench -p mortar-lsp --bench r20_lsp_resolver -- r20_lsp_parser/canonical_generated_read --warm-up-time 0.1 --measurement-time 0.1 --sample-size 10
```

R20.6 uses Criterion `0.7.0`, because Mortar declares Rust `1.85` and
Criterion `0.8.2` requires Rust `1.86`. The benchmark groups are:

- tree-sitter parser latency for canonical generated reads, supported local
  metamodel aliases, supported read-namespace aliases, unsupported alias
  fail-closed syntax, and deterministic large Java documents;
- editor features for hover, copy SQL, PostgreSQL EXPLAIN code actions,
  definition, metadata/source-map/snapshot reads, supported aliases,
  unsupported aliases, stale source maps, and missing snapshots;
- diagnostics and current full-buffer change behavior, including malformed
  buffers, large documents, and success-to-failure-to-recovery edit scripts.

R20.6 records local/internal tooling evidence only. Criterion output under
`rust/target/criterion` must not be committed and cannot support JDBC,
database, Java runtime, or public product performance claims. Allocation
interpretation remains blocked until retained profiler/allocation artifacts are
captured with exact command, commit, corpus, toolchain, OS/CPU, limitations,
and review notes.

Changed files/docs: `rust/Cargo.toml`, `rust/Cargo.lock`,
`rust/crates/mortar-lsp/Cargo.toml`,
`rust/crates/mortar-lsp/src/lib.rs`,
`rust/crates/mortar-lsp/benches/r20_lsp_resolver.rs`,
`rust/crates/mortar-lsp/tests/r20_lsp_benchmark_harness.rs`,
`rust/crates/mortar-lsp/tests/support/r20_lsp_benchmark.rs`,
`docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
`docs/performance.md`, this plan, and `docs/roadmap.md`.

Migration note: no Java public API, runtime behavior, generated Java API,
metadata format, source-map format, VS Code command contract, R19 editor
semantics, runtime optimization, release, publication, migration, or public
performance claim changed.

Verification passed on 2026-06-03:

- `cd rust && cargo fmt --all --check`;
- `cd rust && cargo clippy --all-targets --all-features -- -D warnings`;
- `cd rust && cargo test`;
- focused R20.6 Criterion smoke generated local `rust/target/criterion` output
  as uncommitted build output;
- `gradlew.bat check --no-daemon`;
- `cd editors/vscode && bun run typecheck`;
- `git diff --check`;
- private path/project scrub excluding build, cache, dependency, generated, and
  target outputs.

### R20.7 Evidence-Ranked Optimizations

Status: Done.

R20.7 ranks optimization candidates by retained evidence and closes with no
optimization implementation authorized. The required xhigh
performance/architecture debate concluded that R20.3-R20.6 prove harness
coverage, scenario coverage, and Java/Rust measurement boundaries, but they do
not provide retained repeated artifacts reviewed enough to rank an optimization
candidate for implementation.

The evidence-ranked table is recorded in
`docs/benchmarks/r20-performance-gate.md`. Java runtime candidates are recorded
separately from Rust tooling candidates:

- Java runtime candidates: generated binder/mapper tightening, prepared
  generated-query lifecycle changes, renderer reuse or pre-render caching, PgJDBC
  default/tuning changes, and benchmark threshold tightening.
- Rust tooling candidates: parser construction reuse or parser caching,
  source-map/snapshot lookup caching, diagnostics full-buffer scan
  optimization, and incremental parse or partial-sync strategy.

Every candidate is blocked for implementation until retained raw artifacts,
exact commands, clean commit metadata, environment metadata, dataset or corpus
notes, limitations, profiler/allocation evidence where needed, derived summary,
and reviewer notes identify a dominant cost. Controlled fake-JDBC evidence
remains excluded from PostgreSQL, driver, database, and product performance
claims.

Changed docs: `docs/benchmarks/r20-performance-gate.md`,
`docs/benchmarks/r20-benchmark-readiness.md`, `docs/benchmarks/README.md`,
`docs/performance.md`, this plan, and `docs/roadmap.md`.

Migration note: no Java public API, runtime behavior, generated Java API,
metadata format, source-map format, VS Code command contract, R19 editor
semantics, Rust tooling behavior, benchmark threshold, release, publication,
migration, optimization, or public performance claim changed.

### R20.8 Public Performance Report Gate

Status: Done.

R20.8 closes with a public-report no-go decision. The existing
`docs/benchmarks/performance-report-2026-06-01.md` remains an internal
public-readiness draft because it cites local build-output JSON, has pending
release commit metadata, does not attach retained workflow artifacts, does not
record independent reviewer sign-off against retained artifacts, and does not
include broader workload evidence for application-level claims.

Public wording must say internal/local evidence only unless retained artifacts
exist for the exact claim. R20 does not publish a report, prepare a release,
tag, push, PR, merge, migration, or public performance claim. Broad claims such
as "Mortar is faster than JDBC" remain invalid because they do not name the
exact JDBC baseline, workload, confidence evidence, limitations, and retained
artifact bundle.

Benchmark-readiness review result: public performance reporting is blocked.
The blocker is missing retained, reproducible, reviewable evidence for any
public claim, not a known performance regression.

R20 is Done as a measurement and decision-gate phase. Follow-up work is
deferred to a later retained-artifact review or optimization slice. R21
AI/agent-friendly work is a later authoring-guidance slice and is not
implemented by R20. R22 scalar/mutation contract work, R23 retained performance
evidence and optimization, and R24 pre-release readiness remain deferred.

## R21 Canonical Plan: AI-Friendly Authoring And Query Recipes

Status: Done.

R21 makes Mortar easier for Java developers and AI coding agents to use
correctly on the first try. It does not change Java public APIs, generated APIs,
runtime behavior, editor semantics, benchmark thresholds, release posture, or
performance claims.

### R21 Scope Decision

The required xhigh architecture debate concluded that R21 should be a narrow
docs-and-existing-examples convergence slice, not a product/API slice. The
existing Spring Boot and Clean Architecture examples already compile the
canonical generated-read and adapter-boundary recipe shapes, so R21 does not
add a duplicate fixture module.

Accepted scope:

- one canonical AI-friendly query authoring and recipe guide;
- copyable recipes for generated `findById`, generated `findAll`, `.named(...)`,
  testkit SQL assertions, Spring repository adapters, Clean Architecture
  adapter boundaries, and common mistakes;
- AI-agent invariants for fixed-read facades, raw SQL avoidance, SQL
  visibility, source-map/snapshot contracts, self-executing query rejection,
  and performance-claim restraint;
- concise troubleshooting updates for generated-read editor visibility and
  authoring mistakes;
- careful comparison wording for Mortar, jOOQ, QueryDSL, and JPA/Hibernate
  without replacement or performance claims;
- roadmap and plan updates with evidence and remaining risks.

Rejected scope:

- runtime performance optimization;
- public benchmark or performance claims;
- broad DSL redesign;
- generated repositories or Spring Data-style method derivation;
- ORM behavior;
- app migration;
- Maven/GitHub release, tag, publish, PR, push, or announcement work;
- compatibility hacks or duplicate docs not linked from canonical entry points.

### R21 Implementation Record

Changed docs:

- `docs/query-recipes.md`: canonical AI-friendly authoring guide and recipes.
- `README.md`: canonical docs entry.
- `docs/getting-started.md`: onboarding path to R21 recipes.
- `docs/usage-guide.md`: broader query-path guidance link.
- `docs/spring-boot-postgres-example.md`: Spring example link.
- `docs/ddd-clean-architecture-example.md`: Clean Architecture example link.
- `docs/examples/spring-clean-architecture-repository.md`: legacy example link.
- `docs/comparison.md`: concise Mortar/jOOQ/QueryDSL/JPA selection guidance.
- `docs/troubleshooting.md`: generated-read metadata/source-map diagnostics.
- `docs/roadmap.md` and this plan: status, evidence, and risks.

Compile-backed recipe evidence remains in:

- `examples/spring-boot-postgres/src/main/java/dev/mortar/examples/springpostgres/ClientRepository.java`;
- `examples/spring-boot-postgres/src/test/java/dev/mortar/examples/springpostgres/ClientRepositoryTest.java`;
- `examples/clean-architecture-postgres/src/main/java/dev/mortar/examples/cleanpostgres/PostgresClientReader.java`;
- `examples/clean-architecture-postgres/src/test/java/dev/mortar/examples/cleanpostgres/PostgresClientReaderTest.java`.

Completed exit criteria:

- the new guide is linked from canonical docs and not dead;
- examples in the guide match compiling repository examples;
- docs contain no private paths, usernames, or internal project leakage;
- docs make no unsupported performance, release, migration, replacement, or API
  expansion claims;
- focused example/testkit checks pass;
- full Java, Rust, VS Code, whitespace, scrub, and review gates pass.

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

- Project standards preserved: no wildcard imports, no dead code, no API
  expansion, and no unverified completion claim.
- Clean Architecture boundaries preserved: Mortar remains documented inside
  infrastructure adapters; domain/application ports expose DTOs and business
  methods.
- Public docs quality preserved: the new guide is linked from README, getting
  started, usage, example, comparison, and troubleshooting docs.
- No overclaiming found: R21 makes no performance, replacement, release,
  publish, migration, or benchmark claim.
- Review follow-up: the R21 recipe authoring rules were corrected to avoid
  presenting deferred `count` and `exists` scalar-query contracts as current
  DSL authoring paths.
- Remaining risk: snippets are excerpts from compiling examples, not a new
  standalone recipe module. The xhigh debate accepted this to avoid duplicate
  fixture drift.

Research basis:

- VS Code custom instructions document always-on and file-scoped instruction
  files for AI-assisted coding.
- OpenAI prompt guidance supports explicit role/workflow guidance, examples,
  relevant context, and testing instructions for coding tasks.
- Spring Data JPA documents query-method derivation, declared string queries,
  named parameters, and custom implementations, validating the need for clear
  repository ergonomics without copying Spring Data behavior.
- jOOQ documents SQL building, code generation, and SQL execution as a mature
  SQL-first workflow.
- QueryDSL documents type-safe SQL-like Java queries and SQL/binding
  extraction.
- LSP 3.17 documents hover, code action, and definition as position-based
  editor capabilities, supporting Mortar's metadata/source-map-backed editor
  guidance without editor-owned SQL semantics.

## Future Maturity Gates

R17-R20 are completed maturity gates recorded above. They produced the
real-query coverage gate, contract-hardening gate, Java call-resolution and
editor-semantics hardening gate, and R20 performance measurement/public-claim
decision gate. R21 completed the AI/agent-friendly authoring guidance gate.

Deferred future maturity work remains out of scope here:

- R22 scalar and mutation contracts for real repository persistence flows.
- R23 retained performance evidence and optimization.
- R24 pre-release readiness.
- Retained-artifact benchmark review or optimization slices that may follow
  R20 only when retained evidence authorizes the exact claim or change.

No private application migration, generated repository expansion, release,
publication, public performance report, or public demo claim is authorized by
this handoff.

## R22 Planned Gate: Scalar And Mutation Contracts

Status: Planned.

R22 should make Mortar cover the minimum real repository persistence cycle
before pre-release readiness is evaluated: read, count, check existence, create,
update, delete, and optionally batch writes where the existing API can support
them without surface explosion.

R22 exists because Mortar is not merely a read-query helper. Its product
identity is Java-first, refactor-safe, SQL-transparent persistence code for
Spring/PostgreSQL applications. A first public alpha should not imply ORM
behavior, but it should have clear contracts for common repository reads,
scalar reads, and mutations.

### R22 Scope Decision

R22 must start with xhigh architecture debate before implementation. The debate
should challenge whether scalar reads and mutations belong in one gate or need
to be split, but the planning default is one persistence-cycle gate because
repositories usually need these operations together.

Accepted planning scope:

- scalar query contracts: `count` and `exists`;
- mutation contracts: insert, update, delete, and batch writes only if existing
  mutation foundations can support them cleanly;
- visible SQL, parameters, parameter types, metadata, snapshots, and testkit
  assertions for scalar and mutation paths;
- JDBC execution contracts that preserve existing runtime boundaries;
- Spring repository and Clean Architecture examples for create/update/delete,
  `count`, and `exists`;
- query recipe updates for scalar and mutation authoring;
- diagnostics for unsupported scalar/mutation shapes;
- ADR if public scalar or mutation contracts change architecture or API shape.

Rejected planning scope:

- ORM behavior, dirty checking, managed entity state, lazy loading, aggregate
  graph loading, or implicit relation persistence;
- generated repositories or Spring Data-style method-name derivation;
- self-executing generated query or mutation objects;
- generated optional-filter matrices or generated write method explosion;
- raw SQL as the primary write path;
- runtime performance optimization or public benchmark claims;
- private application migration;
- release, tag, publish, PR, push, or announcement work.

### R22 Exit Criteria

R22 must not be marked Done until:

- scalar and mutation API boundaries are documented and tested;
- existing mutation foundations are reviewed against the new repository-facing
  contracts before new API is added;
- Java compile/tests, Testcontainers-backed PostgreSQL behavior, Rust tooling
  checks, VS Code typecheck, whitespace, scrub, and review gates pass;
- examples compile and prove Mortar stays inside infrastructure adapters;
- docs state that Mortar remains explicit persistence, not an ORM;
- no performance, release, migration, or replacement claims are added.

## R23 Planned Gate: Retained Performance Evidence And Optimization

Status: Planned.

R23 should finish the performance program properly before pre-release readiness
is evaluated. R20 created the measurement discipline, benchmark harnesses, and
public-claim policy. R23 must produce retained, repeated, reviewable evidence
on the post-R22 API surface, then optimize only if the evidence identifies a
dominant cost.

Planned scope:

- run repeated Java runtime JMH/PostgreSQL benchmark workflows on a clean commit
  and retain raw artifacts;
- run generated fixed-read, scalar, mutation, broader DSL, join/page, and batch
  scenarios where supported by R22;
- keep JDBC, PgJDBC-tuned JDBC, jOOQ, and QueryDSL reference comparisons fair
  and explicitly bounded;
- run Rust LSP/tooling benchmarks separately from Java runtime evidence;
- capture commands, environment metadata, dataset/corpus notes, limitations,
  raw JSON, derived summaries, and reviewer notes;
- profile allocation and latency before proposing optimizations;
- implement only evidence-ranked optimizations with before/after retained
  artifacts and review;
- decide whether any public performance wording is allowed, and keep no-go if
  evidence is insufficient.

Rejected scope:

- optimizing from local smoke output;
- public performance claims without retained artifacts and reviewer sign-off;
- changing API shape only for benchmark convenience;
- mixing Rust tooling latency with Java/JDBC runtime claims;
- release, tag, publish, PR, push, migration, or announcement work.

R23 must not be marked Done until either evidence-backed optimizations are
implemented and re-measured, or the retained evidence explicitly supports a
no-optimization decision. In both cases, public claims remain blocked unless the
exact claim has retained artifacts and reviewer sign-off.

## R24 Planned Gate: Pre-Release Readiness

Status: Planned.

R24 should evaluate whether Mortar is ready for a first public alpha after R22
has closed the repository persistence-cycle surface and R23 has completed
retained performance evidence and any justified optimization work.

Planned scope:

- public API review and Javadocs review;
- README, getting started, recipes, troubleshooting, comparison, release policy,
  and changelog review;
- Maven Central and GitHub release dry-run verification;
- Rust crate dry-run verification where applicable;
- CI status and branch protection review;
- public docs scrub for private paths, usernames, local build-output claims,
  and unsupported performance statements;
- benchmark-report go/no-go remains blocked unless retained evidence exists;
- explicit `0.1.0-alpha` go/no-go decision.

R24 does not authorize release, tag, publish, or announcement by itself. It only
produces the readiness decision and any remaining blocker list.

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
