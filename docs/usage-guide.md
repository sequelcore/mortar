# Usage Guide

Mortar is for Java/Spring applications that want refactor-safe query code and
visible SQL. It is not an ORM and does not own aggregate loading, lazy loading,
identity maps, or transaction policy.

## Choosing The Query Path

Use generated fixed read facades for common stable reads:

- primary-key lookup with `QClient.CLIENT.read(renderer).findById(id)`;
- explicit full-table reads for small reference data with
  `QClient.CLIENT.read(renderer).findAll()`;
- repository paths where SQL and parameters should be inspectable before
  explicit `MortarJdbcClient` execution.

Name generated facade queries on the immutable bound query value:

```java
MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT
    .read(renderer)
    .findById(7L)
    .named("ClientRepository.findById");

Optional<QClient.FindByIdRow> row = jdbcClient.fetchOptional(query);
```

The older direct generated executor methods remain available for hot paths that
need the `MortarGeneratedQuery` contract, but the fixed `Read` facade is the
canonical R16.2 repository shape.

Use the Java DSL when the query has application-specific predicates, joins,
projection shape, sorting, or pagination:

```java
QuerySpec query = db.from(CLIENT)
    .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
    .where(client -> client.active.eq(true))
    .orderBy(client -> client.id.asc())
    .page(MortarPage.of(1, 20))
    .named("PostgresClientReader.findActivePage")
    .build();
```

Use pre-rendered `RenderedQuery` only when a caller has already rendered and
verified SQL. Keep raw SQL fragments behind `unsafeWhereRaw(...)` and test them
at the adapter boundary.

## Spring Boot Wiring

The starter wires:

- `MortarDb`;
- PostgreSQL `QueryRenderer`;
- `MortarJdbcClient`;
- optional JDBC logging;
- optional actuator diagnostics.

Explicit starter properties:

```yaml
mortar:
  dialect: postgres
  sql-format: compact
  jdbc:
    logging:
      enabled: false
  diagnostics:
    enabled: true
```

`mortar.dialect` currently supports PostgreSQL only. Add a user-provided
`QueryRenderer`, `MortarJdbcClient`, or `MortarJdbcLogger` bean when application
policy needs custom wiring.

## Clean Architecture Placement

Keep Mortar in infrastructure adapters:

- domain types do not import Mortar;
- application ports expose business methods;
- infrastructure repositories build `QuerySpec` or generated queries;
- tests assert SQL at the adapter boundary.

See `examples/clean-architecture-postgres` for a CI-compiling example.

## Testing Queries

Use fast adapter tests for expected SQL:

```java
MortarBoundQuery<QClient.FindByIdRow> query = QClient.CLIENT.read(renderer)
    .findById(7L)
    .named("ClientRepository.findById");

MortarSqlAssertions.assertThatSql(query)
    .hasSql("select c.id, c.name, c.active from clients c where c.id = ?")
    .hasParameters(7L)
    .hasParameterTypes(Long.class);
```

For hand-built DSL queries, assert the renderer output at the same adapter
boundary:

```java
MortarSqlAssertions.assertThatSql(renderer.render(query))
    .hasSql("select c.id, c.name from clients c where c.active = ? order by c.id asc limit ? offset ?")
    .hasParameters(true, 20, 20);
```

Use Testcontainers when claiming PostgreSQL syntax or execution compatibility.
Use JMH and retained raw JSON artifacts before making performance claims.

## Diagnostics

Compile-time processor diagnostics catch invalid generated metadata before
runtime. Runtime/static query diagnostics help identify unbounded collection
queries, select-all projections, pagination without ordering, and repeated SQL
patterns. Actuator diagnostics report the active starter wiring without
exposing database credentials or parameter values.
