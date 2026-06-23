# Troubleshooting

For canonical repository recipes, see
[`query-recipes.md`](query-recipes.md).

## Generated Q Type Is Missing

Run:

```bash
gradlew.bat compileJava
```

Check that the module has:

```kotlin
compileOnly(project(":java:processor"))
annotationProcessor(project(":java:processor"))
```

Also confirm the model has `@MortarEntity` and exactly one `@MortarId`.

Mortar processes `@MortarEntity` models by default. It does not process every
`jakarta.persistence.Entity` in the module unless JPA discovery is explicitly
enabled. This lets Spring Data JPA applications add Mortar one query slice at a
time without changing existing ORM entities.

If a module intentionally wants Mortar to generate metamodels directly from JPA
annotations, opt in explicitly:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Amortar.jpaDiscovery=true")
}
```

Prefer `@MortarEntity` row models for new Mortar code. Use JPA discovery only
for simple mappings that are intentionally shared with Mortar.

## SQL Does Not Match The Expected Query

Add a focused SQL assertion:

```java
MortarSqlAssertions.assertThatSql(new PostgresQueryRenderer().render(query))
    .hasSql("select c.id, c.name from clients c where c.id = ?")
    .hasParameters(7L);
```

Then update or check SQL snapshots with the CLI described in `docs/cli.md`.

For scalar or mutation contracts, assert the bound value before changing
repository behavior:

```java
MortarSqlAssertions.assertThatSql(repository.deactivateMutation(7L))
    .hasSql("update clients set active = ? where id = ?")
    .hasParameters(false, 7L);
```

If a returning mutation maps columns in the wrong order, check the mutation's
`returning` list. Row mapping follows the PostgreSQL `RETURNING` column order,
while metadata still includes assigned, filtered, and returned columns for
inspection.

## IntelliJ Or VS Code Shows No SQL

For generated fixed reads such as `QClient.CLIENT.read(renderer).findById(id)`,
editor tooling expects fresh generated metadata, source maps, and a matching
`mortar.sql.snap.json` snapshot. If hover, copy SQL, EXPLAIN, or definition
returns no result, first check that annotation processing regenerated
`META-INF/mortar` outputs and that the snapshot key still exists.

Explicit markers are the legacy/manual path:

```java
// mortar:snapshot=ClientRepository.findActiveById
```

The nearest `mortar.sql.snap.json` must contain the same snapshot name and a
non-blank SQL string.

## PostgreSQL EXPLAIN Does Not Run

For VS Code or IntelliJ, verify:

- the Mortar CLI is on `PATH` or configured by editor settings;
- the PostgreSQL connection string is configured;
- the snapshot resolves at the current caret;
- the database is reachable from the editor process.

## Gradle Fails On Processing Warnings

Mortar compiles with `-Werror`. Spring annotations can produce processing
warnings in example modules that also run the Mortar processor. Add this only to
modules where another annotation processor is intentionally not claiming Spring
annotations:

```kotlin
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-processing")
}
```
