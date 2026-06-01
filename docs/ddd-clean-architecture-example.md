# DDD And Clean Architecture Example

Mortar should live in the infrastructure adapter, not inside domain entities or
use cases.

## Boundary Rule

- Domain model: no Mortar imports.
- Application/use case ports: expose business methods, not SQL concepts.
- Infrastructure repository: builds `QuerySpec` and calls `MortarJdbcClient`.
- Tests: assert repository SQL at the adapter boundary.

## Example Layout

```text
application/
  ClientLookup.java
domain/
  ClientId.java
  ClientSummary.java
infrastructure/postgres/
  Client.java
  ClientRepository.java
```

`examples/spring-boot-postgres` keeps the sample compact, but the repository
method shows the same boundary: public methods return domain-facing results,
while the Mortar query is isolated in `findActiveByIdQuery`.

## Verification

```bash
gradlew.bat :examples:spring-boot-postgres:test
```

The test asserts the SQL without requiring a database connection. That keeps the
adapter contract visible and fast while deeper PostgreSQL behavior remains
covered by Testcontainers in the runtime and dialect modules.

## Rule Of Thumb

If a use case needs to know about `QuerySpec`, `ColumnRef`, or generated `Q*`
types, the boundary has leaked. Keep those types inside the persistence adapter.
