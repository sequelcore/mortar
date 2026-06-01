# Static Diagnostics

Mortar static diagnostics analyze query intent before execution. They operate on
`QuerySpec` in `java/core`, so they do not require Spring, JDBC, PostgreSQL, or
Rust tooling in the runtime path.

## Query Diagnostics

```java
List<MortarDiagnostic> diagnostics = QueryDiagnostics.analyze(query);
```

Current rules:

- `MORTAR_CORE_002` warns when a collection query has no `limit`.
- `MORTAR_CORE_005` warns when a query uses the default select-all projection.
- `MORTAR_CORE_006` warns when a paginated query has no `orderBy`.
- `MORTAR_CORE_007` warns when an `in` predicate has more than 100 values.
- `MORTAR_CORE_008` warns when a nullable relationship is joined with an inner
  join.
- `MORTAR_CORE_009` warns when the same rendered SQL pattern appears more than
  10 times in an analyzed query batch.
- `MORTAR_CORE_010` adds an informational index advisory for columns used in
  filters, joins, or ordering.

Diagnostics are warnings by default unless explicitly marked as `INFO`. Schema
drift checks are available through `mortar schema check`. A suppression mechanism
with mandatory reason text is still planned.
