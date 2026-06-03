# Spring Clean Architecture Repository

This example shows how a Spring Boot application should use Mortar from an infrastructure adapter while keeping domain and application code independent from Spring, JDBC, and Mortar runtime details.

The compiled fixture is `java/spring-boot-starter/src/test/java/dev/mortar/spring/MortarCleanArchitectureRepositoryExampleTest.java`.
It demonstrates Spring auto-configuration and port wiring with a lower-level
DSL query. For the canonical R16 generated `Read` facade flow, use the
CI-compiling `examples/clean-architecture-postgres` module.

For R21 copyable repository recipes and AI-agent authoring rules, use
[`../query-recipes.md`](../query-recipes.md).

## Boundary

- Domain owns the repository port and domain result types.
- Infrastructure implements the port using `MortarJdbcClient`.
- Spring wires the adapter through `MortarAutoConfiguration`.
- Core business code does not depend on Spring, JDBC, PostgreSQL, or generated SQL.

## Domain Port

```java
interface ClientRepository {
    Optional<ClientSummary> findSummaryById(long clientId);
}

record ClientSummary(long id, String name) {
}
```

## Infrastructure Adapter

```java
final class MortarClientRepository implements ClientRepository {
    private static final TableRef CLIENTS = new TableRef("clients", "c");
    private static final ColumnRef<Long> ID = CLIENTS.column("id", "id", Long.class);
    private static final ColumnRef<String> NAME = CLIENTS.column("name", "name", String.class);

    private final MortarJdbcClient jdbcClient;

    MortarClientRepository(MortarJdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ClientSummary> findSummaryById(long clientId) {
        List<ClientSummary> rows = jdbcClient.fetch(
            new SimpleMortarDb()
                .from(CLIENTS)
                .select(ID, NAME)
                .where(ID.eq(clientId))
                .build(),
            resultSet -> new ClientSummary(resultSet.getLong("id"), resultSet.getString("name"))
        );

        return rows.stream().findFirst();
    }
}
```

## Spring Wiring

```java
@Configuration(proxyBeanMethods = false)
class InfrastructureConfiguration {
    @Bean
    ClientRepository clientRepository(MortarJdbcClient jdbcClient) {
        return new MortarClientRepository(jdbcClient);
    }
}
```

`MortarAutoConfiguration` supplies `MortarDb`, `QueryRenderer`, and `MortarJdbcClient`. The application only declares its domain port implementation.

## Rules

- Do not place `MortarJdbcClient` in domain services.
- Do not expose `ResultSet` outside infrastructure.
- Keep generated table references inside infrastructure adapters or dedicated persistence mapping classes.
- Return domain records, DTOs, or aggregates from the port.
- Use Spring transactions outside the adapter; Mortar participates through the auto-configured transaction-aware data source.
