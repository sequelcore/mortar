# Mortar Build Metadata

The Java annotation processor emits metadata for CLI, snapshot, and editor
tooling. The metadata is tooling input only; it does not add a runtime
dependency to Spring applications.

## Entity Metadata

The processor writes entity metadata at:

```text
META-INF/mortar/entities.json
```

Current format: `mortar-metadata-v1`.

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

Query entries are tooling metadata. They identify generated fixed-read shapes,
parameters, row types, generated source symbols, and default snapshot keys.

The metadata does not include rendered SQL, JDBC binding APIs, Spring beans,
editor command names, execution methods, or repository call-site mappings.

The CLI can read the file directly:

```bash
mortar inspect --metadata-file build/classes/java/main/META-INF/mortar/entities.json
```

## Source Maps

The processor also writes source-map metadata at:

```text
META-INF/mortar/source-map.json
```

Current format: `mortar-source-map-v1`.

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

The source map is a freshness-checked companion keyed by the same query IDs as
`mortar-metadata-v1`. It stores stable source anchors instead of javac
line/column locations.

Freshness fingerprints are semantic. They include query identity, generated
symbol identity, entity table/alias metadata, ordered column metadata, relation
metadata, ordered parameter metadata, row type, and snapshot key. They do not
include timestamps, absolute paths, temp directories, usernames, local build
paths, rendered SQL, or full generated source text.

Rust tooling must treat missing, mismatched, or stale source-map entries as a
fail-closed condition before hover, navigation, or copy-SQL features use the
data.
