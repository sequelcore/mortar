# Mortar Build Metadata

The Java annotation processor emits build metadata for Rust tooling at:

```text
META-INF/mortar/entities.json
```

The current format is `mortar-metadata-v1`.

```json
{
  "format": "mortar-metadata-v1",
  "entities": [
    {
      "java_type": "example.Client",
      "table": "clients",
      "alias": "c",
      "columns": [
        {
          "property": "id",
          "column": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "relations": []
    }
  ]
}
```

Relation metadata includes `nullable` when relation paths are emitted by the
processor. R9 diagnostics use that field to warn about nullable inner joins.

The CLI can read the file directly:

```bash
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json
```

This metadata is tooling input only. It does not add a runtime dependency to
Spring applications.
