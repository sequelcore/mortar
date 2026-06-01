# IntelliJ

Mortar's IntelliJ plugin project lives in `editors/intellij`.

The plugin is a secondary editor integration. VS Code remains the primary editor
path, and shared SQL transparency behavior should stay in the Rust LSP or Mortar
CLI when possible.

## Build

```bash
gradlew.bat :editors:intellij:buildPlugin
```

The plugin targets IntelliJ IDEA 2026.1.2, depends on the bundled Java plugin,
and uses the official IntelliJ Platform Gradle Plugin.

```bash
gradlew.bat :editors:intellij:verifyPluginStructure
gradlew.bat :editors:intellij:test
```

Marketplace metadata lives in `editors/intellij/marketplace`.

Publishing is intentionally explicit and token-gated:

```bash
set PUBLISH_TOKEN=...
gradlew.bat :editors:intellij:publishPlugin
```

## SQL Hover

The current hover path mirrors the R11 LSP smoke contract:

- Java source contains `// mortar:snapshot=Repository.method` immediately before
  the method.
- A `mortar.sql.snap.json` file exists in the same directory or an ancestor
  directory.
- IntelliJ quick documentation renders the generated SQL from that snapshot.

This marker-based routing is transitional until generated Java source maps exist.

## Snapshot Navigation

The plugin registers a goto-declaration handler that opens the nearest
`mortar.sql.snap.json` file from a Java PSI element. R12.3 is covered by an
IntelliJ `BasePlatformTestCase` fixture that configures a Java file and verifies
the snapshot file target.

## Copy SQL

The editor context menu contributes `Copy Generated SQL`. The action uses the
same marker and snapshot lookup as quick documentation, then writes the resolved
SQL to the IDE clipboard.

## PostgreSQL EXPLAIN

The editor context menu contributes `Run PostgreSQL EXPLAIN`. Configure these
IDE properties before running it:

- `dev.mortar.cli.path`: path to the Mortar CLI executable, defaults to
  `mortar`;
- `dev.mortar.postgres.connection`: PostgreSQL connection string.

The plugin invokes `mortar explain --connection ... --sql ...` in a background
process and reports the CLI output through IntelliJ notifications. The plugin
does not open database connections directly.

## Diagnostics

The plugin registers a Java annotator that warns when a
`// mortar:snapshot=...` marker cannot be resolved:

- missing nearest `mortar.sql.snap.json`;
- malformed or non-canonical snapshot file;
- missing snapshot entry.

The adapter validates the same canonical snapshot shape used by the Rust
tooling: `format` must be `mortar-sql-snapshot-v1`, snapshot names must be
unique and non-blank, and SQL must be non-blank.

## Quick Fixes

When the nearest snapshot file is missing, IntelliJ offers
`Create mortar.sql.snap.json`. The quick fix creates a canonical empty snapshot
file next to the current Java source file.

The plugin does not generate placeholder SQL for a missing snapshot entry.
Mortar should only write executable SQL when the compiler/tooling can prove the
source query.

## Boundaries

- Do not add IntelliJ dependencies to `java/core`, dialects, runtime adapters,
  or the Spring Boot starter.
- IntelliJ UI code belongs under `editors/intellij`.
- Database access for EXPLAIN actions must go through Mortar CLI boundaries, not
  directly through the IntelliJ plugin.
- JaCoCo coverage verification is disabled for this IDE adapter because the
  IntelliJ test runner uses its own classloader; keep pure parsing/rendering
  logic covered by normal tests.
