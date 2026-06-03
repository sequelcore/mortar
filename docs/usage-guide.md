# Usage Guide

Mortar is for Java/Spring applications that want refactor-safe query code and
visible SQL. It is not an ORM and does not own aggregate loading, lazy loading,
identity maps, or transaction policy.

For copyable repository recipes and AI-agent authoring invariants, start with
[`query-recipes.md`](query-recipes.md). This guide explains the broader query
path selection.

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

The generated `Read` facade is deliberately small. It does not generate
execution methods, writes, joins, optional filters, scalar methods,
projections, or repository classes. R22 `count` and `exists` are DSL scalar
contracts, not generated facade methods. Repositories still own method names,
row to DTO mapping, transaction placement, and tests.

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

Use scalar DSL terminals for `count` and `exists` when a repository needs a
single value without loading rows:

```java
MortarBoundScalar<Long> count = db.from(CLIENT)
    .where(client -> client.active.eq(true))
    .count(renderer)
    .named("ClientRepository.countActive");

long activeClients = jdbcClient.fetchOne(count);
```

Use explicit mutation specs and bound mutation values for writes:

```java
MortarBoundMutation deactivate = MortarBoundMutation.unnamed(
        new UpdateSpec(
            CLIENT.table,
            List.of(Assignment.of(CLIENT.active, false)),
            List.of(CLIENT.id.eq(id)),
            List.of()
        ),
        renderer
    )
    .named("ClientRepository.deactivate");

int changedRows = jdbcClient.execute(deactivate);
```

Use `MortarReturningMutation<T>` only when the PostgreSQL mutation declares
`RETURNING` columns and the repository maps those returned columns into a row or
DTO type.

For create paths that expect one returned row, fetch the returning mutation as a
list and fail fast unless exactly one row is returned. Do not turn a missing
`RETURNING` row into `Optional.empty()` unless the business operation explicitly
allows no returned row.

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
- infrastructure repositories build `QuerySpec` values or generated
  `MortarBoundQuery<?>` read facade values, scalar values, or explicit mutation
  values;
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

Scalar and mutation values use the same assertion entry point:

```java
MortarSqlAssertions.assertThatSql(count)
    .hasName("ClientRepository.countActive")
    .hasSql("select count(*) from clients c where c.active = ?")
    .hasParameters(true)
    .hasParameterTypes(Boolean.class);
```

Use Testcontainers when claiming PostgreSQL syntax or execution compatibility.
Use JMH and retained raw JSON artifacts before making performance claims.

Repository SQL tests catch query drift before a database is involved. If a Java
entity field, table mapping, selected column, query parameter, or renderer
contract changes, assertions such as `hasSql(...)`, `hasParameters(...)`, and
`hasParameterTypes(...)` fail in the adapter test where the repository query is
owned.

## Diagnostics

Compile-time processor diagnostics catch invalid generated metadata before
runtime. Runtime/static query diagnostics help identify unbounded collection
queries, select-all projections, pagination without ordering, and repeated SQL
patterns. Actuator diagnostics report the active starter wiring without
exposing database credentials or parameter values.
