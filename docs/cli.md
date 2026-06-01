# Mortar CLI

The Mortar CLI is Rust tooling for SQL-transparent Java projects. It is not in
the Spring runtime hot path.

## Doctor

```bash
mortar doctor
mortar doctor --json
```

`doctor` prints the active toolchain status and canonical snapshot format. It is
intended for local setup checks and CI diagnostics.

Current text output:

```text
mortar toolchain ready
snapshot format: mortar-sql-snapshot-v1
```

## Inspect SQL

```bash
mortar inspect --sql " select 1 "
mortar inspect --sql " select 1 " --json
```

`inspect --sql` validates non-blank SQL input and prints the normalized SQL text.
Future R8 slices will attach CI-friendly JSON output.

## Inspect Metadata

```bash
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json --json
```

`inspect --metadata-file` reads the metadata emitted by the Java annotation
processor and prints a compact entity inventory.

## Explain

```bash
mortar explain --connection "postgres://postgres:postgres@localhost:5432/app" --sql "select 1"
```

`explain` connects to PostgreSQL and runs `EXPLAIN (format text)` for the SQL.
Use a read-only or disposable database connection for local and CI diagnostics.
If the connection fails, credentials in the connection string are redacted before
the error is printed.

## Report

```bash
mortar report --metadata-file build/classes/java/main/META-INF/mortar/entities.json
mortar report --metadata-file build/classes/java/main/META-INF/mortar/entities.json --json
```

`report` prints a compact query inventory from Mortar build metadata: entity
count, table aliases, column counts, and relation counts.

## Schema Check

```bash
mortar schema check --connection "postgres://postgres:postgres@localhost:5432/app" --metadata-file build/classes/java/main/META-INF/mortar/entities.json
```

`schema check` compares generated Mortar metadata against PostgreSQL
`information_schema.columns` and reports missing tables or columns.

## Redaction

Mortar redacts sensitive parameter names containing `password`, `passwd`,
`secret`, `token`, `api_key`, or `authorization`. PostgreSQL connection strings
also redact user-info passwords and sensitive query parameters before appearing
in CLI diagnostics.

## Snapshot Update

```bash
mortar snapshot check --file mortar.sql.snap.json
mortar snapshot check --file mortar.sql.snap.json --json
```

`snapshot check` validates that a snapshot file is parseable and canonical. It
fails when entries are not in canonical order or formatting differs from the
tool-rendered file.

```bash
mortar snapshot update --file mortar.sql.snap.json --name ClientRepository.findById --sql "select c.id from clients c where c.id = ?"
```

Snapshot format details live in [`sql-snapshots.md`](sql-snapshots.md).
