# Market Notes

Date: 2026-05-31

## Observed Pain

- String-based queries are fragile under refactor.
- Hibernate can hide SQL shape and performance costs.
- JPA Criteria is type-aware but verbose.
- QueryDSL improves type safety but still leaves room for a clearer Spring-first product experience.
- jOOQ is strong and transparent, but SQL-first.

## Mortar Position

Mortar targets the gap between Hibernate opacity and jOOQ's SQL-first mental model:

> Java-first query authoring with SQL transparency.

## Reference Areas

- Spring Data JPA query methods and specifications.
- jOOQ code generation and SQL builder.
- QueryDSL generated query types.
- Ebean query beans.
- SQLDelight and SQLx compile-time validation patterns.
- Research on database access bugs in Java applications.
