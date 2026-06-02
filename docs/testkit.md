# Mortar Testkit

The Mortar testkit makes rendered SQL part of normal Java tests. It is built on
AssertJ and depends only on `java/core`.

SQL snapshot file format and CLI update behavior are documented in
[`sql-snapshots.md`](sql-snapshots.md).

## SQL Assertions

Use `MortarSqlAssertions.assertThatSql` with a `RenderedQuery` from any dialect
renderer or a framework-free `MortarBoundQuery<?>`.

```java
import static dev.mortar.testkit.MortarSqlAssertions.assertThatSql;

assertThatSql(renderedQuery)
    .hasSql("select c.id from clients c where c.id = ?");
```

R16.1 `MortarBoundQuery<?>` assertions unwrap the canonical `RenderedQuery`.
The testkit still depends only on `java/core`; JDBC row mapping remains outside
the testkit contract.

`renders(...)` is kept as a readable alias for `hasSql(...)`.

## Parameter Assertions

Assert parameter values separately from SQL text so CI failures point to the
actual contract that changed.

```java
assertThatSql(renderedQuery)
    .hasParameters(7L, "active")
    .hasParameterTypes(Long.class, String.class);
```

Failure messages include the expected value, the actual value, and the SQL that
produced the parameters.

## Metadata Assertions

Rendered query metadata can be asserted with the same generated/table references
used by application code.

```java
assertThatSql(renderedQuery)
    .hasTables(CLIENTS, ROUTES)
    .hasColumns(CLIENTS.id(), ROUTES.clientId())
    .hasJoins(clientRouteJoin);
```

Metadata assertion failures include the SQL so a CI log shows both the
refactor-safe Java reference and the rendered query contract.

## Explain Plan Assertions

Integration tests can execute database-specific `EXPLAIN` statements and assert
the returned plan text without Mortar owning the test datasource.

```java
import static dev.mortar.testkit.MortarExplainPlanAssertions.assertThatExplainPlan;

assertThatExplainPlan(explainPlan)
    .containsNode("Index Scan")
    .usesIndex("clients_pkey")
    .doesNotUseSequentialScan("clients");
```

These assertions are intentionally string-based so the testkit stays independent
from JDBC, Spring, and PostgreSQL driver APIs.

## Renderer Regression Fixtures

Dialect modules should use the testkit against their own edge-case fixture
suites. The PostgreSQL dialect keeps a focused fixture suite for nested boolean
grouping, explicit unsafe raw parameter order, and pretty SQL pagination so
snapshot-facing SQL changes are intentional.
