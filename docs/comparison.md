# Comparison

Mortar is Java-domain-first and SQL-transparent. It is not trying to be a full
ORM or a SQL-first DSL.

## Mortar vs JPA Criteria

JPA Criteria is type-aware but verbose and hard to scan. Mortar keeps the query
as Java code while making SQL output a first-class artifact through renderers,
snapshots, CLI, and editor tooling.

## Mortar vs QueryDSL

QueryDSL validates generated query types as a strong Java model. Mortar follows
that direction but narrows scope around PostgreSQL-first rendering, explicit
runtime boundaries, and SQL transparency tooling.

## Mortar vs jOOQ

jOOQ is mature, SQL-first, and very strong when the developer wants to model SQL
directly. Mortar starts from Java persistence models and repository code, then
exposes the generated SQL for inspection.

Use jOOQ when SQL is the primary authoring language. Use Mortar when Java
repository code is the primary authoring language but the team still wants SQL
visibility.

## Mortar vs Spring Data @Query

`@Query` is direct and familiar, but text strings are not refactor-safe. Mortar
keeps columns, DTO projections, predicates, and parameters in Java code.

## Mortar vs Hibernate ORM

Hibernate can manage object graphs. Mortar deliberately avoids hidden lazy
loading, implicit graph traversal, and surprise N+1 behavior. Queries are
explicit, rendered, and testable.
