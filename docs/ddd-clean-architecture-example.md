# DDD And Clean Architecture Example

Mortar should live in the infrastructure adapter, not inside domain entities or
use cases.

The runnable companion module is `examples/clean-architecture-postgres`. It
compiles in CI and shows a domain-facing `ClientReader` port implemented by a
PostgreSQL infrastructure adapter.

## Boundary Rule

- Domain model: no Mortar imports.
- Application/use case ports: expose business methods, not SQL concepts.
- Infrastructure repository: builds generated `MortarBoundQuery<?>` read
  facade values or hand-built `QuerySpec` values, then calls
  `MortarJdbcClient`.
- Tests: assert repository SQL at the adapter boundary.

## Example Layout

```text
application/
  ClientLookup.java
domain/
  ClientId.java
  ClientSummary.java
infrastructure/postgres/
  Client.java
  ClientRepository.java
```

`examples/clean-architecture-postgres` keeps the sample compact: public methods
return domain-facing results, while generated `Q*` types, `QuerySpec`, and
`MortarJdbcClient` stay inside `PostgresClientReader`.

For common fixed reads, the infrastructure adapter uses the generated `Read`
facade and maps the generated row back to the domain-facing result:

```java
public Optional<ClientSummary> findById(long id) {
    return jdbcClient.fetchOptional(
            CLIENT.read(renderer)
                .findById(id)
                .named("PostgresClientReader.findById")
        )
        .map(row -> new ClientSummary(row.id(), row.name()));
}
```

For richer reads, the same adapter keeps the Java DSL inside infrastructure:

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

## Verification

```bash
gradlew.bat :examples:clean-architecture-postgres:check
```

The test asserts the SQL without requiring a database connection. That keeps the
adapter contract visible and fast while deeper PostgreSQL behavior remains
covered by Testcontainers in the runtime and dialect modules.

The adapter test asserts generated read SQL directly from the bound query:

```java
MortarBoundQuery<QClient.FindByIdRow> query = CLIENT.read(renderer)
    .findById(7L)
    .named("PostgresClientReader.findById");

assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);
```

## Rule Of Thumb

If a use case needs to know about `QuerySpec`, `ColumnRef`, or generated `Q*`
types, the boundary has leaked. Keep those types inside the persistence adapter.
