# Migration From Spring Data @Query

Mortar is useful when string SQL or JPQL starts hiding references that should be
refactor-safe Java.

## Before

```java
@Query("""
    select c.id, c.name
    from clients c
    where c.id = :id and c.active = true
    """)
Optional<ClientSummary> findActiveById(long id);
```

The query can drift when a Java property, DTO constructor, table, or column is
renamed.

## After

```java
QuerySpec query = db.from(CLIENT)
    .projectRecord(ClientSummary.class, client -> client.id, client -> client.name)
    .where(client -> client.id.eq(id))
    .where(client -> client.active.eq(true))
    .named("ClientRepository.findActiveById")
    .build();
```

## Migration Steps

1. Move the repository method behind an application port if it is not already.
2. Add a Mortar-annotated persistence model for the table.
3. Compile and use the generated `Q*` metamodel.
4. Recreate the filter, projection, sort, and pagination in Mortar Java code.
5. Add a SQL assertion with `java/testkit`.
6. Add or update a SQL snapshot so CLI/editor tooling can inspect the rendered
   query.

## Keep Spring Data Where It Fits

Do not migrate simple CRUD only for churn. Mortar is meant for queries where
transparency, refactor safety, SQL snapshots, or performance review matter.
