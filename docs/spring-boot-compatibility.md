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

## Current Baseline

- Spring Boot autoconfigure: `3.5.14`
- Spring Boot starter test: `3.5.14`
- Spring Boot starter JDBC: `3.5.14`
- Spring Boot starter Actuator: `3.5.14`

The canonical version source is `java/spring-boot-starter/build.gradle.kts`.
