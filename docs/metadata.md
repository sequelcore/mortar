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
            "member": "read.findById",
            "generated_type": "example.QClient.Read"
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

R16.2 records the canonical generated read facade member, such as
`read.findById` or `read.findAll`, while keeping query IDs and snapshot keys
stable. The older generated executor classes can still exist in generated Java,
but metadata points tooling at the current fixed read facade.

The metadata does not include rendered SQL, JDBC binding APIs, Spring beans,
editor command names, execution methods, or repository call-site mappings. Existing
`mortar-metadata-v1` files without `queries` remain parseable by the Rust
compiler.

The CLI can read the file directly:

```bash
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json
```

This metadata is tooling input only. It does not add a runtime dependency to
Spring applications.

## Source Map And Freshness Metadata

R18.3 adds a sibling artifact for generated fixed-read source maps:

```text
META-INF/mortar/source-map.json
```

The current source-map format is `mortar-source-map-v1`.

```json
{
  "format": "mortar-source-map-v1",
  "metadata": {
    "format": "mortar-metadata-v1",
    "path": "META-INF/mortar/entities.json",
    "fingerprint": "sha256:..."
  },
  "queries": [
    {
      "id": "example.Client.findById",
      "entity_type": "example.Client",
      "generated_entity_type": "example.QClient",
      "generated_read_namespace": "example.QClient.Read",
      "generated_member": "read.findById",
      "query_name": "findById",
      "snapshot": "example.Client.findById",
      "row_type": "example.QClient.FindByIdRow",
      "parameters": [
        {
          "name": "id",
          "java_type": "java.lang.Long"
        }
      ],
      "source_anchor": {
        "kind": "java-type",
        "java_type": "example.Client",
        "member": "findById"
      },
      "freshness": {
        "fingerprint": "sha256:..."
      }
    }
  ]
}
```

The source-map artifact does not replace `mortar-metadata-v1`; it is a
freshness-checked companion keyed by the same query IDs. It deliberately stores
stable source anchors instead of javac line/column locations. Java annotation
processing only provides portable originating-element hints at compilation-unit
granularity, and annotation-processing diagnostic locations may be approximate.

Freshness fingerprints are semantic. They include query identity, generated
symbol identity, entity table/alias metadata, ordered column metadata, relation
metadata, ordered parameter metadata, row type, and snapshot key. They do not
include timestamps, absolute paths, temp directories, usernames, local build
paths, rendered SQL, or full generated source text.

Rust tooling must treat missing, mismatched, or stale source-map entries as a
fail-closed condition before any editor hover, navigation, or copy-SQL feature
uses the data. R18.3 only defines and parses the contract; editor behavior
remains later R18 scope.
