# Mortar Project Guide

Mortar is a public Sequel-built project, but it does not use the Sequel name as product branding.

## Mission

Build a Java + Rust query system for Java-first, refactor-safe, SQL-transparent persistence in Spring applications.

## Architecture Rules

- `java/core` is domain/core model only. It must not depend on Spring, JDBC, JPA, PostgreSQL, or IntelliJ.
- Dialects render SQL. They do not execute queries.
- Runtime adapters execute rendered plans. They do not own query semantics.
- Spring Boot is an adapter.
- Rust is tooling/compiler infrastructure, not the default per-query runtime path.
- Public APIs must be small, readable, and stable.

## Quality Gates

- Java 21 minimum.
- Gradle wrapper required.
- No wildcard imports.
- JaCoCo minimum coverage: 80%.
- Rust code must pass `cargo fmt`, `cargo clippy`, and `cargo test`.
- Every architecture decision belongs in `docs/adr`.

## Canonical Planning

- `docs/roadmap.md` is the canonical long-term roadmap.
- Every completed roadmap slice must update `docs/roadmap.md` in the same change.
- A slice is not done unless implementation, tests, docs, and verification evidence are all represented.
