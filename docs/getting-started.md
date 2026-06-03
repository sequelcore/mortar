# Getting Started

Mortar is Java-first query code with visible SQL output.

The quickest runnable example is `examples/spring-boot-postgres`.

## Verify The Example

```bash
gradlew.bat :examples:spring-boot-postgres:test
```

That command compiles the annotated Java entity, runs the Mortar annotation
processor, uses the generated `QClient` metamodel, renders PostgreSQL SQL, and
asserts the SQL and parameters.

## Core Flow

1. Annotate a Java model with `@MortarEntity`, `@MortarId`, and
   `@MortarColumn`.
2. Let the annotation processor generate a `Q*` metamodel and fixed read
   facade.
3. Write repository queries against generated Java fields.
4. Render or execute the query through PostgreSQL/JDBC adapters.
5. Use SQL snapshots, CLI, LSP, VS Code, or IntelliJ to inspect the generated
   SQL.

## Minimal Query Shape

```java
QuerySpec query = db.from(CLIENT)
    .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
    .where(client -> client.id.eq(7L))
    .where(client -> client.active.eq(true))
    .named("ClientRepository.findActiveById")
    .build();
```

This code is refactorable Java. The PostgreSQL renderer produces:

```sql
select c.id, c.name from clients c where c.id = ? and c.active = ?
```

For common read paths, the generated `Q*` type exposes a fixed read facade:

```java
List<QClient.FindAllRow> rows =
    jdbcClient.fetch(
        QClient.CLIENT.read(renderer)
            .findAll()
            .named("ClientRepository.findAll")
    );
```

Primary-key lookups bind the identifier into the rendered query without a
manual generated parameter record:

```java
MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT
    .read(renderer)
    .findById(7L)
    .named("ClientRepository.findById");

assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);

Optional<QClient.FindByIdRow> row = jdbcClient.fetchOptional(query);
```

The facade renders SQL through the configured renderer, exposes SQL and
parameters through `MortarBoundQuery`, supports direct testkit assertions, and
still requires explicit execution through `MortarJdbcClient`.

Use `docs/sql-snapshots.md` and `docs/cli.md` when you want snapshot checks or
offline inspection.

Next, read [`query-recipes.md`](query-recipes.md) for copyable generated-read,
repository-adapter, and SQL assertion recipes. Then use
[`usage-guide.md`](usage-guide.md) for broader query-path selection, starter
diagnostics, and benchmark evidence.
