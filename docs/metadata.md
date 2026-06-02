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
      "relations": [],
      "queries": [
        {
          "id": "example.Client.findById",
          "name": "findById",
          "shape": "findById",
          "generated_source": {
            "java_type": "example.QClient",
            "member": "findById",
            "generated_type": "example.QClient.FindByIdQuery"
          },
          "parameters": [
            {
              "name": "id",
              "java_type": "java.lang.Long"
            }
          ],
          "row_type": "example.QClient.FindByIdRow",
          "snapshot": "example.Client.findById"
        }
      ]
    }
  ]
}
```

Relation metadata includes `nullable` when relation paths are emitted by the
processor. R9 diagnostics use that field to warn about nullable inner joins.

R16.1 adds optional `queries` entries. A query entry is tooling metadata only:

- `id`: stable generated query identifier scoped to the entity and query
  shape;
- `name`: generated query member name;
- `shape`: fixed query kind such as `findAll` or `findById`;
- `generated_source`: generated Java symbol information that can be mapped to
  source later without changing query semantics;
- `parameters`: ordered generated parameter names and Java types;
- `row_type`: generated row type name;
- `snapshot`: default snapshot key.

The metadata does not include rendered SQL, JDBC binding APIs, Spring beans,
editor command names, or repository call-site mappings. Existing
`mortar-metadata-v1` files without `queries` remain parseable by the Rust
compiler.

The CLI can read the file directly:

```bash
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json
```

This metadata is tooling input only. It does not add a runtime dependency to
Spring applications.
