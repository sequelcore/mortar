# ADR-0001: Java-First SQL-Transparent Architecture

Date: 2026-05-31

## Status

Accepted

## Context

Spring Boot applications often rely on repository methods, JPQL strings, native SQL strings, Criteria API, QueryDSL, jOOQ, or Hibernate-generated SQL. Each option has a tradeoff between refactor safety, readability, SQL control, and runtime transparency.

Mortar exists for developers who are stronger in Java than SQL but still need to understand and optimize the SQL their code emits.

## Decision

Mortar will use Java as the primary authoring surface and expose generated SQL as a first-class artifact.

Java owns:

- public DSL;
- annotation processing;
- generated metamodels;
- JDBC runtime;
- Spring integration.

Rust owns:

- CLI tooling;
- query inspection;
- SQL snapshots;
- future LSP support;
- benchmark and analysis helpers.

Rust will not be required on the hot path for normal Spring query execution.

## Consequences

- Java users get IDE refactor safety and familiar APIs.
- SQL remains visible for logs, tests, inspections, and explain plans.
- The project avoids ORM opacity while still avoiding handwritten query strings.
- The system must maintain strict boundaries so tooling does not leak into runtime.
