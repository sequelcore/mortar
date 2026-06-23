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

Consumers need Java artifact version `0.1.0-alpha.2` for this behavior.
`0.1.0-alpha.1` does not contain the JPA discovery default change or the UUID
fixed-read helper fix.
