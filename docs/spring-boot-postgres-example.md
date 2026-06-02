# Spring Boot PostgreSQL Example

The canonical Spring Boot example lives in `examples/spring-boot-postgres`.

## What It Shows

- Spring Boot 3.5 wiring through `java/spring-boot-starter`.
- PostgreSQL rendering through `java/dialect-postgres`.
- JDBC execution boundary through `MortarJdbcClient`.
- Generated Java metamodels through `java/processor`.
- Generated `Read` facade usage for common `findAll` and `findById` read paths.
- Testkit assertions for SQL and parameters.

## Run The Example Tests

```bash
gradlew.bat :examples:spring-boot-postgres:test
```

The test does not need a local database. It verifies the query contract by
rendering SQL and by mocking the JDBC client boundary.

## Important Files

- `examples/spring-boot-postgres/src/main/java/dev/mortar/examples/springpostgres/Client.java`
- `examples/spring-boot-postgres/src/main/java/dev/mortar/examples/springpostgres/ClientRepository.java`
- `examples/spring-boot-postgres/src/test/java/dev/mortar/examples/springpostgres/ClientRepositoryTest.java`
- `examples/spring-boot-postgres/src/main/resources/application.yml`
- `examples/spring-boot-postgres/src/main/resources/schema.sql`

## Repository Pattern

Use the generated `Read` facade for common read paths:

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

The generated facade renders SQL through the configured renderer, returns an
immutable `MortarBoundQuery`, and still executes only when passed to
`MortarJdbcClient`.

## Starter Properties

The example configures the starter explicitly:

```yaml
mortar:
  dialect: postgres
  sql-format: pretty
  jdbc:
    logging:
      enabled: true
  diagnostics:
    enabled: true
```

`mortar.dialect` currently supports PostgreSQL. The diagnostics endpoint reports
the selected dialect, SQL format, JDBC logging flag, diagnostics flag, and
renderer class so production applications can verify the active starter wiring.

Keep richer query shapes in the Java DSL:

```java
public Optional<ClientSummary> findActiveById(long id) {
    List<ClientSummary> rows = jdbcClient.fetch(findActiveByIdQuery(id), ClientSummary.class);
    return rows.stream().findFirst();
}
```

Keep query construction in a named method so tests and editor tooling have a
stable reference:

```java
QuerySpec findActiveByIdQuery(long id) {
    return db.from(CLIENT)
        .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
        .where(client -> client.id.eq(id))
        .where(client -> client.active.eq(true))
        .named("ClientRepository.findActiveById")
        .build();
}
```

## Local Database

The example `application.yml` expects:

```text
jdbc:postgresql://localhost:5432/mortar_example
```

The module includes `schema.sql` and `data.sql` for a small `clients` table.
For automated database compatibility, use the Testcontainers integration tests
in the main PostgreSQL/runtime modules.
