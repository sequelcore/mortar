# AI-Friendly Query Recipes

Mortar is easiest to use correctly when repository code follows a small set of
repeatable shapes. This guide is the canonical R21 authoring guide for humans
and AI agents.

The snippets below are backed by the compiling examples in
`examples/spring-boot-postgres` and `examples/clean-architecture-postgres`.
Use those modules as the source of truth when copying code into an application.

## Authoring Rules

- Prefer generated fixed-read facades when the entity has the query shape you
  need: `read(renderer).findById(...)` or `read(renderer).findAll()`.
- Use the Java DSL for supported application-specific predicates, joins,
  projections, sorting, pagination, and writes.
- Treat `count` and `exists` as future scalar-query contracts until R22
  approves and implements them explicitly.
- Name every repository-owned query with `.named("Repository.method")`.
- Execute through `MortarJdbcClient`; generated query objects do not execute
  themselves.
- Keep `Q*`, `QuerySpec`, `MortarBoundQuery<?>`, and `MortarJdbcClient` inside
  infrastructure adapters.
- Assert SQL, parameters, and parameter types at the adapter boundary.
- Do not write raw SQL unless the query explicitly needs an unsupported shape,
  and keep raw fragments behind `unsafeWhereRaw(...)`.
- Do not make public performance claims without retained benchmark artifacts
  and review.

## Generated `findById`

Use the generated fixed-read facade for primary-key lookup. Map the generated
row type to an application-facing DTO after execution.

```java
public Optional<ClientSummary> findById(long id) {
    return jdbcClient.fetchOptional(
            CLIENT.read(renderer)
                .findById(id)
                .named("ClientRepository.findById")
        )
        .map(row -> new ClientSummary(row.id(), row.name()));
}
```

The query value is inspectable before execution:

```java
MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT.read(renderer)
    .findById(7L)
    .named("ClientRepository.findById");

assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);
```

## Generated `findAll`

Use `findAll()` only for explicit full-table reads, typically small reference
data or bounded fixtures. Do not use it as the default for arbitrary production
tables.

```java
public List<ClientSummary> findAll() {
    return jdbcClient.fetch(
            CLIENT.read(renderer)
                .findAll()
                .named("ClientRepository.findAll")
        )
        .stream()
        .map(row -> new ClientSummary(row.id(), row.name()))
        .toList();
}
```

## Richer Repository Queries

When the query has application-specific predicates, sorting, or pagination,
use the Java DSL and keep the query construction named and testable.

```java
QuerySpec activePageQuery(int page, int size) {
    return db.from(CLIENT)
        .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
        .where(client -> client.active.eq(true))
        .orderBy(client -> client.id.asc())
        .page(MortarPage.of(page, size))
        .named("PostgresClientReader.findActivePage")
        .build();
}
```

Assert the rendered SQL at the same adapter boundary:

```java
assertThatSql(renderer.render(query))
    .hasSql("select c.id, c.name from clients c where c.active = ? order by c.id asc limit ? offset ?")
    .hasParameters(true, 20, 20);
```

## Spring Repository Adapter Pattern

Spring repositories should be ordinary infrastructure adapters. Mortar does not
generate Spring repository classes and does not derive queries from method
names.

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

For DDD or Clean Architecture, keep the port domain-facing and put Mortar in
the PostgreSQL adapter:

```java
public interface ClientReader {
    Optional<ClientSummary> findById(long id);
}
```

The adapter implements that port with generated reads or DSL queries. Domain
services and use cases should not import Mortar types.

## AI Agent Instructions

When generating or editing Mortar repository code:

1. Read `CLAUDE.md`, `docs/usage-guide.md`, this guide, and the relevant
   example module before changing code.
2. Start from generated `Q*` constants and `read(renderer)` for fixed reads.
3. Add `.named(...)` on the immutable bound query value.
4. Keep execution in `MortarJdbcClient`; never add `query.fetch(...)` or other
   self-executing generated query APIs.
5. Keep SQL visible through `MortarSqlAssertions`, snapshots, or rendered query
   assertions before claiming the query is correct.
6. Do not bypass source-map, metadata, or snapshot contracts for editor
   tooling.
7. Do not generate repositories, optional-filter method matrices, hidden joins,
   lazy loading, aggregate loaders, writes, batches, `count`, or `exists`
   helpers unless a later roadmap slice explicitly adds that API.
8. Do not add raw SQL unless the requested query cannot be represented by the
   current generated facade or DSL. If raw SQL is required, use the explicit
   unsafe API and add adapter-boundary SQL tests.
9. Do not add runtime optimizations, benchmark thresholds, public performance
   claims, releases, tags, publishes, migrations, or announcement work as part
   of authoring recipes.

## What Not To Do

Do not put Mortar in domain services:

```java
// Wrong: application code now depends on persistence infrastructure.
public final class ClientLookupService {
    private final MortarJdbcClient jdbcClient;
}
```

Do not invent generated repository APIs:

```java
// Wrong: Mortar does not generate Spring Data-style repositories.
clientRepository.findByNameAndStatusAndCreatedAt(name, status, createdAt);
```

Do not hide execution on the query object:

```java
// Wrong: generated query values are inspectable values, not executors.
CLIENT.read(renderer).findById(id).fetch(jdbcClient);
```

Do not skip SQL evidence:

```java
// Wrong: this gives no adapter-boundary SQL contract.
assertThat(repository.findById(7L)).isPresent();
```

## Choosing Mortar, jOOQ, QueryDSL, Or JPA

Use Mortar when Java repository code is the primary authoring surface and the
team wants generated Java fields, refactor safety, PostgreSQL rendering, and
visible SQL assertions.

Use jOOQ when SQL itself is the primary model and the team wants a mature
SQL-first DSL, broad SQL feature coverage, and schema-driven SQL construction.

Use QueryDSL when generated Java query types are a good fit across the team's
chosen backend and Mortar's narrower PostgreSQL/Spring transparency contract is
not the deciding factor.

Use JPA or Hibernate when the application needs ORM behavior such as managed
entity state, identity maps, aggregate graph loading, or lazy relationships.
Mortar intentionally does not provide those behaviors.

## Troubleshooting Checklist

- Missing `Q*` type: run `gradlew.bat compileJava` and check annotation
  processor wiring.
- Missing generated read method: confirm the model has exactly one `@MortarId`
  and is processed by `MortarProcessor`.
- Unexpected SQL: add or update a focused `MortarSqlAssertions.assertThatSql`
  test before changing application behavior.
- Missing editor SQL for generated reads: check fresh `META-INF/mortar`
  metadata, source maps, and `mortar.sql.snap.json`; explicit snapshot markers
  are only the legacy/manual path.
- Performance question: consult `docs/performance.md`; do not infer runtime
  claims from local smoke output.

## Research Basis

- VS Code custom instructions document concise project-wide and file-scoped
  instruction files for AI-assisted coding:
  https://code.visualstudio.com/docs/agent-customization/custom-instructions
- OpenAI prompt guidance recommends explicit role/workflow guidance, examples,
  relevant context, and testing instructions for coding tasks:
  https://platform.openai.com/docs/guides/prompt-engineering
- Spring Data JPA documents derived query methods, declared queries, named
  parameters, and custom implementations:
  https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html
- jOOQ documents SQL building, code generation, and SQL execution as mature
  SQL-first workflows: https://www.jooq.org/doc/latest/manual
- QueryDSL documents type-safe SQL-like queries for Java, and its SQL query
  API exposes rendered SQL and bindings:
  https://querydsl.com/ and
  https://querydsl.com/static/querydsl/5.0.0/apidocs/com/querydsl/sql/SQLQuery.html
- LSP 3.17 documents hover, code action, and definition capabilities as
  position-based editor features:
  https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/
