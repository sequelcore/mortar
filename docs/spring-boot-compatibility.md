# Spring Boot Compatibility

Mortar's Spring Boot starter follows Spring Boot's current auto-configuration model: `@AutoConfiguration`, conditional beans, configuration properties, and `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

## Matrix

| Spring Boot line | Status | Verification | Notes |
| --- | --- | --- | --- |
| 3.5.x | Tested | `java/spring-boot-starter` compiles and tests against Spring Boot 3.5.14 | Primary supported line for the current Mortar development cycle. |
| 4.x | Planned | Not yet compiled in CI | Support requires a dedicated compatibility lane before being marked tested. |

## Compatibility Rules

- Mortar core must remain Spring-free.
- `java/runtime-jdbc` must remain Spring-free.
- Spring transaction participation belongs only in `java/spring-boot-starter`.
- New Spring Boot properties must be covered by `ApplicationContextRunner` tests.
- `mortar.dialect` is explicit even while PostgreSQL is the only supported
  starter dialect, so future dialects require a deliberate public property
  addition instead of hidden auto-detection.
- A Spring Boot line is not considered tested until CI compiles and runs the starter tests against that line.

## Incremental Adoption In JPA Applications

Mortar is designed to coexist with existing Spring Data JPA applications during
incremental adoption. Adding the Mortar processor to a module does not make every
`jakarta.persistence.Entity` a Mortar model.

The default processor boundary is explicit:

- `@MortarEntity` models are processed by default.
- `jakarta.persistence.Entity` models are ignored by default.
- JPA annotation discovery is available only when a module opts in with
  `-Amortar.jpaDiscovery=true`.

This keeps adoption scoped to intentional read and query slices. Existing JPA
entities can continue to serve ORM-managed aggregate persistence while Mortar
models describe the SQL-facing rows a repository adapter wants to query. Mortar
does not manage entity state, lazy loading, dirty checking, cascades, or
repository method-name derivation.

The opt-in JPA discovery mode is a compatibility bridge, not the recommended
default for new Mortar code. Prefer dedicated `@MortarEntity` row models when a
module already contains mature JPA entities, especially if those entities use
ORM-specific relationships, generated identifiers, embedded identifiers,
converters, lifecycle callbacks, or constructor rules.

UUID primary keys are supported in generated fixed-read helpers. A row model
with a `java.util.UUID` `@MortarId` compiles without requiring a no-argument UUID
constructor or consumer-specific generated-code workarounds.

The boundary follows current platform practice:

- Spring Data JPA is the Spring repository abstraction for Jakarta Persistence:
  https://docs.spring.io/spring-data/jpa/docs/current/reference/html/
- Spring Data JDBC documents a separate aggregate-oriented JDBC model:
  https://docs.spring.io/spring-data/jdbc/docs/current/reference/html/
- Gradle annotation processors should declare and preserve predictable
  incremental behavior:
  https://docs.gradle.org/current/userguide/java_plugin.html#sec:incremental_annotation_processing
- PostgreSQL supports UUID as a native type:
  https://www.postgresql.org/docs/current/datatype-uuid.html

## Current Baseline

- Spring Boot autoconfigure: `3.5.14`
- Spring Boot starter test: `3.5.14`
- Spring Boot starter JDBC: `3.5.14`
- Spring Boot starter Actuator: `3.5.14`

The canonical version source is `java/spring-boot-starter/build.gradle.kts`.
