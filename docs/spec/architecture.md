# Mortar Architecture

## Module Boundaries

`java/core` defines the query model. It is framework-free.

`java/dialect-postgres` converts query models into PostgreSQL SQL.

`java/runtime-jdbc` executes rendered SQL through JDBC.

`java/spring-boot-starter` wires Mortar into Spring Boot applications.

`java/processor` generates Java metamodel sources. Generated sources may target
`java/runtime-jdbc` contracts for executable hot paths, but the processor module
itself does not render SQL or execute queries.

`java/testkit` provides SQL assertions and future snapshot testing.

`rust/crates/mortar-compiler` owns non-runtime analysis.

`rust/crates/mortar-cli` exposes developer commands.

## Dependency Direction

Adapters depend on core. Core depends on nothing in the adapter layer.

```text
spring-boot-starter -> runtime-jdbc -> core
spring-boot-starter -> dialect-postgres -> core
testkit -> core
processor -> core
```

Generated application source can depend on runtime contracts:

```text
generated Q* executor -> runtime-jdbc -> core
```
