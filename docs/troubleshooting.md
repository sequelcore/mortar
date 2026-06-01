# Troubleshooting

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

## SQL Does Not Match The Expected Query

Add a focused SQL assertion:

```java
MortarSqlAssertions.assertThatSql(new PostgresQueryRenderer().render(query))
    .hasSql("select c.id, c.name from clients c where c.id = ?")
    .hasParameters(7L);
```

Then update or check SQL snapshots with the CLI described in `docs/cli.md`.

## IntelliJ Or VS Code Shows No SQL

Editor tooling currently needs a canonical snapshot file and a marker:

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
