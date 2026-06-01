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
2. Let the annotation processor generate a `Q*` metamodel and canonical
   `findById` executor.
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

For primary-key lookup hot paths, the generated `Q*` type also exposes a
generated executor:

```java
var query = QClient.CLIENT.findById(renderer);
var parameters = new QClient.FindByIdParameters(7L);

Optional<QClient.FindByIdRow> row = jdbcClient.fetchOptional(query, parameters);
```

The executor pre-renders SQL through the configured renderer once, then uses
direct JDBC binders and projection-index row mapping on execution.

Use `docs/sql-snapshots.md` and `docs/cli.md` when you want snapshot checks or
offline inspection.
