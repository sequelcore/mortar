# R26 Incremental Spring/JPA Adoption Plan

Date: 2026-06-23
Status: Verified

This plan records the R26 processor hardening slice. The canonical public status
source remains [`roadmap.md`](roadmap.md).

## Objective

Harden Mortar for incremental adoption by existing Spring/JPA applications
without changing Mortar into an ORM.

## Non-Goals

- Do not make JPA entities the preferred Mortar modeling surface.
- Do not add ORM state, lazy loading, identity maps, or hidden SQL.

## Slices

| Slice | Files | Verification |
| --- | --- | --- |
| R26.1 Documentation basis | `docs/spring-boot-compatibility.md`, `docs/migration-from-spring-data-query.md` | Public documentation explains the Spring/JPA adoption boundary, opt-in JPA discovery, UUID support, and supporting platform references. |
| R26.2 Explicit JPA discovery opt-in | `java/processor/src/main/java/dev/mortar/processor/MortarProcessor.java` | Processor ignores `jakarta.persistence.Entity` by default and honors `-Amortar.jpaDiscovery=true`. |
| R26.3 UUID fixed-read helper support | `java/processor/src/main/java/dev/mortar/processor/MortarProcessor.java` | Generated fixed-read helpers compile for `java.util.UUID` IDs. |
| R26.4 Processor tests | `java/processor/src/test/java/dev/mortar/processor/MortarProcessorGenerationTest.java` | Focused compile-backed tests cover default JPA ignore, opt-in JPA generation, and UUID ID helpers. |
| R26.5 Troubleshooting and roadmap | `docs/troubleshooting.md`, `docs/roadmap.md` | The opt-in flag, adoption boundary, and release status are documented. |

## Verification Criteria

- `cmd.exe /c "gradlew.bat :java:processor:check --no-daemon"`
- If validating a consumer locally before publication:
  `cmd.exe /c "gradlew.bat publishToMavenLocal --no-daemon"`

## Release Recommendation

Consumers need Java artifact version `0.1.0-alpha.2` for this behavior on
Spring Boot 3.5.x. Spring Boot 4.1.x consumers should use `0.1.0-alpha.3`.

## R27 Spring Boot 4.1 Platform Certification

Status: Done.

| Slice | Files | Verification |
| --- | --- | --- |
| R27.1 Spring Boot starter baseline | `java/spring-boot-starter/build.gradle.kts`, `java/spring-boot-starter/src/test/java/dev/mortar/spring/MortarSpringBootCompatibilityTest.java` | Starter tests compile and run against Spring Boot 4.1.0. |
| R27.2 Java 25 toolchain | `build.gradle.kts`, `settings.gradle.kts`, `java/processor/src/main/java/dev/mortar/processor/MortarProcessor.java` | Java modules compile with a Java 25 toolchain; the processor declares `RELEASE_25`. |
| R27.3 Public docs and release metadata | `README.md`, `CHANGELOG.md`, `docs/spring-boot-compatibility.md`, `docs/release.md`, `docs/roadmap.md`, `docs/spring-boot-postgres-example.md`, `.github/workflows/publish.yml` | Public docs state the Spring Boot 4.1 and Java 25 baseline for `0.1.0-alpha.3`. |
`0.1.0-alpha.1` does not contain the JPA discovery default change or the UUID
fixed-read helper fix.
