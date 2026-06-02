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

Status: In Progress

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
- Fixed single-table DTO projection: select an explicit column set into a Java
  record or DTO constructor for the fixed read shapes above.
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

### API Proposal

The examples below are proposed R16 shapes. They are compile-looking design
targets, not current API claims.

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

Proposed R16 generated facade style for primary-key lookup:

```java
public Optional<ClientSummary> findById(long id) {
    QClient.Read client = CLIENT.read(renderer);

    MortarBoundQuery<QClient.Row> query = client.findById(id)
        .named("ClientRepository.findById");

    return jdbcClient.fetchOptional(query)
        .map(row -> new ClientSummary(row.id(), row.name()));
}
```

Expected visible SQL from the same object:

```java
QClient.Read client = CLIENT.read(renderer);
MortarBoundQuery<QClient.Row> query = client.findById(7L)
    .named("ClientRepository.findById");

assertThat(query.sql())
    .isEqualTo("select c.id, c.name, c.active from clients c where c.id = ?");
assertThat(query.parameterTypes())
    .containsExactly(Long.class);
```

Proposed R16 fixed DTO projection style:

```java
public Optional<ClientSummary> findById(long id) {
    MortarBoundQuery<ClientSummary> query = CLIENT.read(renderer)
        .findById(id)
        .projectRecord(ClientSummary.class, c -> c.id, c -> c.name)
        .named("ClientRepository.findById");

    return jdbcClient.fetchOptional(query);
}
```

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
but the repository still owns business method names, projection choices, query
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
                .projectRecord(ClientSummary.class, c -> c.id, c -> c.name)
                .named("ClientRepository.findById")
        );
    }
}
```

The same query must be testable without hitting a database:

```java
MortarBoundQuery<ClientSummary> query = CLIENT.read(renderer)
    .findById(7L)
    .projectRecord(ClientSummary.class, c -> c.id, c -> c.name)
    .named("ClientRepository.findById");

MortarSqlAssertions.assertThatSql(query.rendered())
    .hasSql("select c.id, c.name from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);
```

SQL snapshot expectations stay visible:

```java
MortarSqlSnapshots.assertThat(query)
    .matchesSnapshot("ClientRepository.findById");
```

VS Code hover should show the rendered SQL over the generated query call once
processor-emitted source maps exist. R16 defines the source-map/query-id
contract; R18 hardens call-site hover and navigation against real generated
contracts:

```java
CLIENT.read(renderer)
    .findById(id)
    .projectRecord(ClientSummary.class, c -> c.id, c -> c.name);
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
  leads the user through the approved fixed read method set and projection
  choices;
- Java field renames fail at compile time or annotation-processor time, not as
  runtime string query surprises;
- generated SQL is available from the query object, generated-source Javadocs,
  tests, snapshots, logs, and editor hover;
- no hidden lazy loading, implicit aggregate loading, object identity tracking,
  dirty checking, or repository magic;
- no implicit runtime query construction from method names;
- no execution methods on generated query objects; execution stays in
  `MortarJdbcClient`;
- no generated writes, batches, optional-filter matrices, or joins in R16;
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
- fixed read executors for `findById` and `findAll`;
- bound parameter objects where they materially improve SQL visibility or JDBC
  execution, but repositories should not usually instantiate them manually;
- projection row types for all mapped columns and explicitly requested fixed
  projection shapes;
- query-id and source-map contract records linking generated facade methods,
  Java field metadata, rendered SQL, and snapshot names.

API-surface budget:

- at most one generated read namespace per entity;
- fixed R16 method set: `findById`, `findAll`, `projectRecord`, `projectDto`,
  `named`, `sql`, `rendered`, `parameterTypes`, and `metadata`;
- no overload matrix for optional filter combinations;
- no generated write namespace in R16;
- no generated execution methods such as `query.fetch(jdbcClient)`,
  `query.fetchOptional(jdbcClient)`, `query.count(jdbcClient)`, or
  `query.exists(jdbcClient)`.

Required explicit choices:

- query name through `named(...)`;
- selected projection columns;
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
- do not generate facade methods for relations in R16;
- keep generated public names predictable: `Read`, `Row`, `FindById`,
  `FindAll`, and projection-specific row names only when generated by explicit
  annotations or fixture needs.

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

#### R16.3: Bound Parameters, Visible SQL, And Testkit Contract

Objective: make fixed generated read facades first-class runtime and testkit
inputs so repositories can bind values, inspect SQL, assert metadata, and write
snapshots without custom glue code.

Affected modules/docs: `java/core`, `java/processor`, `java/runtime-jdbc`,
`java/testkit`, `rust/crates/mortar-cli`, `rust/crates/mortar-compiler`,
examples, `docs/api-reference.md`, `docs/usage-guide.md`,
`docs/sql-snapshots.md`, `docs/testkit.md`.

Expected tests: runtime bound-query tests, testkit assertion tests for bound
queries, Rust snapshot update/check tests for generated query metadata, and
repository SQL snapshot tests.

Non-goals: no optional filters, no count/exists, no joins, no generated writes,
no benchmark claims, no snapshot auto-approval.

Acceptance criteria: a generated read query can be rendered, asserted,
snapshotted, diffed, and executed through `MortarJdbcClient` with no execution
method on the generated query object.

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

## Future Maturity Gates

R17 is planned as the real-query coverage gate. It should use a public
non-application-specific fixture app and query corpus to cover optional filters,
joins, stable pagination, count and exists queries, DTO projections,
search-style queries, simple writes, batches, and compile-time or refactor
failure cases for renamed or deleted fields. If a capability is not needed by
that realistic query corpus, it should not enter R17. R17 is where broader
Java-first query shapes prove they are actually simpler than current DSL or
handwritten SQL plus JDBC binding and row mapping.

R18 is planned as stability, refactor-safety, and tooling hardening. It should
cover generator golden tests, source and binary compatibility checks, Gradle
incremental compilation, multi-module cases, metadata-change diagnostics, editor
behavior against real generated contracts, source-map-backed hover/navigation,
stale metadata diagnostics, schema drift workflow, and SQL snapshots as a
normal developer workflow.

R19 is planned as pre-release candidate hardening. It should decide whether
Mortar is ready for a serious public beta or release candidate gate, not
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
