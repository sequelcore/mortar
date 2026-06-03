# Query Recipes

Mortar repository code works best when it follows a small set of repeatable,
testable shapes. The snippets below are backed by the compiling examples in
`examples/spring-boot-postgres` and `examples/clean-architecture-postgres`.

## Authoring Rules

- Prefer generated fixed-read facades for `read(renderer).findById(...)` and
  explicit `read(renderer).findAll()`.
- Use the Java DSL for application-specific predicates, joins, projections,
  sorting, pagination, scalar reads, and writes.
- Use DSL scalar terminals for `count` and `exists`; Mortar does not generate
  scalar facade methods.
- Use explicit mutation specs and bound mutation values for writes; Mortar does
  not generate write facades or repository classes.
- Name every repository-owned query with `.named("Repository.method")`.
- Execute through `MortarJdbcClient`; query values do not execute themselves.
- Keep Mortar types inside infrastructure adapters.
- Assert SQL, parameters, and parameter types at the adapter boundary.
- Keep raw SQL behind explicit unsafe APIs and test it at the adapter boundary.

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
data or bounded fixtures.

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

## DSL Reads

When the query has application-specific predicates, sorting, projection, joins,
or pagination, use the Java DSL and keep the query construction named and
testable.

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

Assert the rendered SQL:

```java
assertThatSql(renderer.render(query))
    .hasSql("select c.id, c.name from clients c where c.active = ? order by c.id asc limit ? offset ?")
    .hasParameters(true, 20, 20);
```

## Count And Exists

Use DSL scalar terminals when the repository needs a single value:

```java
public long countActive() {
    return jdbcClient.fetchOne(
        db.from(CLIENT)
            .where(client -> client.active.eq(true))
            .count(renderer)
            .named("ClientRepository.countActive")
    );
}

public boolean existsActive(long id) {
    return jdbcClient.fetchOne(
        db.from(CLIENT)
            .where(client -> client.id.eq(id))
            .where(client -> client.active.eq(true))
            .exists(renderer)
            .named("ClientRepository.existsActive")
    );
}
```

## Create, Update, And Delete

Use explicit mutation specs. Mutations are inspectable values; execution still
belongs to `MortarJdbcClient`.

```java
public int deactivate(long id) {
    return jdbcClient.execute(deactivateMutation(id));
}

MortarBoundMutation deactivateMutation(long id) {
    return MortarBoundMutation.unnamed(
            new UpdateSpec(
                CLIENT.table,
                List.of(Assignment.of(CLIENT.active, false)),
                List.of(CLIENT.id.eq(id)),
                List.of()
            ),
            renderer
        )
        .named("ClientRepository.deactivate");
}
```

Use `MortarReturningMutation<T>` only when PostgreSQL `RETURNING` columns are
declared and mapped into an adapter-facing row or DTO:

```java
MortarReturningMutation<ClientSummary> createMutation(long id, String name, boolean active) {
    return MortarReturningMutation.unnamed(
            new InsertSpec(
                CLIENT.table,
                List.of(
                    Assignment.of(CLIENT.id, id),
                    Assignment.of(CLIENT.name, name),
                    Assignment.of(CLIENT.active, active)
                ),
                List.of(CLIENT.id, CLIENT.name)
            ),
            renderer,
            ClientSummary.class
        )
        .named("ClientRepository.create");
}
```

When the repository expects `INSERT ... RETURNING` to produce one row, enforce
that cardinality in the adapter.

## Spring Repository Adapter

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

Domain services and use cases should not import Mortar types.

## Avoid These Shapes

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

Use QueryDSL when generated Java query types fit the team's chosen backend and
Mortar's narrower PostgreSQL/Spring transparency contract is not the deciding
factor.

Use JPA or Hibernate when the application needs ORM behavior such as managed
entity state, identity maps, aggregate graph loading, or lazy relationships.
Mortar intentionally does not provide those behaviors.
