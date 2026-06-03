# SQL Snapshots

Mortar SQL snapshots are canonical JSON files owned by the Rust tooling. They
store the SQL contract that Java tests and CI can compare against.

## File Format

The current format is `mortar-sql-snapshot-v1`.
Generated reads, DSL scalar values, row-count mutations, and returning
mutations all use the same snapshot entry shape; R22 does not add a second
snapshot format.

```json
{
  "format": "mortar-sql-snapshot-v1",
  "snapshots": [
    {
      "name": "ClientRepository.findById",
      "sql": "select c.id from clients c where c.id = ?",
      "parameters": [
        {
          "position": 1,
          "java_type": "java.lang.Long",
          "value": "7"
        }
      ],
      "metadata": {
        "tables": ["clients"],
        "columns": ["clients.id"],
        "joins": []
      }
    }
  ]
}
```

Rules:

- `format` must be exactly `mortar-sql-snapshot-v1`.
- `snapshots[].name` must be unique and non-blank.
- `snapshots[].sql` must be non-blank.
- Rendering sorts snapshots by name to keep diffs stable.
- Parameter values are serialized strings for snapshot readability. Sensitive
  parameter names should be passed through Mortar redaction rules before writing
  public or CI-visible snapshots.

## Update Command

Check a snapshot file:

```bash
mortar snapshot check --file mortar.sql.snap.json
```

Use the CLI to create or replace a snapshot entry:

```bash
mortar snapshot update --file mortar.sql.snap.json --name ClientRepository.findById --sql "select c.id from clients c where c.id = ?"
```

The command creates parent folders when needed, creates a new snapshot file when
it does not exist, and replaces an existing snapshot with the same name.
