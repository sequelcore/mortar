# Comparison

Mortar is Java-domain-first and SQL-transparent. It is not trying to be a full
ORM or a SQL-first DSL.

For authoring recipes that show the Mortar repository shape in code, see
[`query-recipes.md`](query-recipes.md).

## Mortar vs JPA Criteria

JPA Criteria is type-aware but verbose and hard to scan. Mortar keeps the query
as Java code while making SQL output a first-class artifact through renderers,
snapshots, CLI, and editor tooling.

## Mortar vs QueryDSL

QueryDSL validates generated query types as a strong Java model. Mortar follows
that direction but narrows scope around PostgreSQL-first rendering, explicit
runtime boundaries, and SQL transparency tooling.

Use QueryDSL when its generated query types fit the team's chosen persistence
backend and Mortar's narrower PostgreSQL/Spring transparency contract is not
the deciding factor.

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

Use JPA or Hibernate when the application needs managed entity state, identity
maps, aggregate graph loading, or lazy relationships. Mortar intentionally does
not provide those behaviors.
