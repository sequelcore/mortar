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

The marker is a transitional explicit contract until Mortar's Java processor emits
proper source maps.

Pending R11 work must replace manual markers with generated source mappings.

## Run

```bash
cd rust
cargo run -p mortar-lsp
```

The process speaks LSP over stdio and is intended to be launched by an editor
extension, not used as an interactive terminal command.
