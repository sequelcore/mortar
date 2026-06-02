# Mortar LSP

Mortar's Language Server Protocol support lives in `rust/crates/mortar-lsp`.

The LSP server is the first editor tooling path because it keeps Mortar useful
in VS Code and other editors before adding IDE-specific plugins. The server is
tooling-only; Java applications do not depend on it at runtime.

## Current Scope

R11.1 establishes the crate, binary entry point, server capability contract, and
stdio initialization/shutdown loop. R11.2 adds SQL hover request routing from an
explicit `mortar:snapshot` marker to `mortar-sql-snapshot-v1` content. R11.3
maps compiler schema drift warnings to LSP diagnostic values and publishes
snapshot-marker diagnostics from document open/change events. R11.4 exposes a
`mortar.copySql` code action carrying the generated SQL as its first argument.
R11.6 adds the matching PostgreSQL `EXPLAIN` code action through the
`mortar.explainSql` editor command.

The server currently advertises:

- full text document sync;
- hover support;
- code action support.

Hover content renders the matching snapshot SQL as a fenced `sql` block. Cursor
position mapping currently uses an explicit source marker:

```java
// mortar:snapshot=ClientRepository.findById
```

The marker is a transitional explicit contract until LSP consumers switch to
the processor-emitted source-map artifact.

R16.1 defines the first minimal query-id/generated-source metadata hook in
`META-INF/mortar/entities.json`. The metadata records stable query IDs and
generated Java symbols only; it does not define editor protocol behavior or
query semantics. R16.1 does not implement source-map-backed hover/navigation.
R18.3 adds parser-ready `META-INF/mortar/source-map.json` data with format
`mortar-source-map-v1`, stable generated fixed-read anchors, and freshness
fingerprints. LSP hover/copy SQL still does not consume that artifact in R18.3;
R18.5 owns editor behavior against real generated facade contracts.

## Run

```bash
cd rust
cargo run -p mortar-lsp
```

The process speaks LSP over stdio and is intended to be launched by an editor
extension, not used as an interactive terminal command.
