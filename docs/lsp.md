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
- code action support;
- definition support for source-map-backed SQL snapshot entries.

Hover content renders the matching snapshot SQL as a fenced `sql` block. R18.5
uses `mortar-source-map-v1` plus fresh `mortar-metadata-v1` as the primary
routing contract for generated fixed-read calls such as:

```java
QClient.CLIENT.read(renderer).findById(id);
QClient.CLIENT.read(renderer).findAll();
```

The LSP uses the generated metamodel context (`QClient.CLIENT` or an explicitly
static-imported generated constant) plus the fixed-read member
(`read.findById` or `read.findAll`) to select one fresh source-map query entry.
It does not resolve entries by `generated_member` alone, because that field is
shared by every generated entity with the same fixed read shape.

The source map provides the snapshot key, and the LSP reads SQL from
`mortar.sql.snap.json`. It does not duplicate rendered SQL in source-map
metadata and does not navigate to generated Java line/column locations.
Definition requests navigate to the matching snapshot entry.

R19.2 recovers generated fixed-read calls with local Java syntax parsing in the
Rust LSP. R19.3 is the current alias-success boundary and adds an
identity-bearing local binding model for two finite alias shapes:

```java
var client = QClient.CLIENT;
client.read(renderer).findById(id);

var read = QClient.CLIENT.read(renderer);
read.findById(id);
```

The syntax parser is used only for bounded same-document facts: import
declarations, target method invocations, immediate `.read(renderer)` receiver
chains, source ranges, and direct local variable initializers that prove one of
the supported alias identities. Fresh `mortar-source-map-v1` plus
`mortar-metadata-v1` remains authoritative for query identity and snapshot
routing, and SQL still comes only from `mortar.sql.snap.json`.

Historical note: R19.2a was the residual hardening slice before R19.3 alias
success. It kept alias success deferred while adding coverage for malformed
full-document Java buffers, full-document open/change recovery, deeply
parenthesized and cast-wrapped canonical receivers, and lambda/catch/switch
alias-scope variants. If tree-sitter can see a generated-looking call in
incomplete Java syntax, the LSP returns no hover, no copy SQL, no EXPLAIN
action, and no definition target, then reports an incomplete-syntax diagnostic.
Unsupported generated-looking alias syntax also fails closed with an
unsupported-syntax diagnostic instead of being reported as stale source-map
evidence.

When a generated fixed-read call is present but its metamodel context cannot be
recovered by local syntax, or metadata/source-map inputs are missing, stale,
mismatched, or ambiguous, the LSP fails closed: hover and code actions return no
SQL, definition returns no target, and diagnostics report a bounded reason code.
R19.4 uses reason codes for unsupported alias syntax, ambiguous aliases,
reassigned aliases, stale or missing source-map evidence, missing SQL snapshots,
and malformed Java buffers.

R19 does not add Java type binding, classpath-aware semantics, helper-returned
receiver support, alias chaining, aliases proven only by type annotations,
field aliases, cross-method or cross-file aliases, wildcard static import
success, ambiguous explicit import collision success, arbitrary DSL call-site
analysis, or editor-side SQL rendering.

R19.5 reviewed editor semantics on 2026-06-02 and found no resolver drift to
fix. Hover, copy SQL, PostgreSQL EXPLAIN, definition, and generated-call
diagnostics all route through the same source-map-backed resolver outcome.
Supported alias shapes remain exactly the two R19.3 local binding forms above,
and unsupported generated-looking aliases fail closed with reason-specific
diagnostics. No new semantic tests were added for R19.5 because the existing
Rust LSP tests and VS Code smoke tests already cover canonical success,
supported alias parity across editor features, unsupported alias diagnostics,
stale or missing source-map fail-closed behavior, and marker non-fallback for
generated calls.

Explicit markers remain a legacy/manual path:

```java
// mortar:snapshot=ClientRepository.findById
```

The marker path is not an R18.5 success condition and must not be used as a
fallback for stale generated fixed-read source-map data.

R16.1 defines the first minimal query-id/generated-source metadata hook in
`META-INF/mortar/entities.json`. The metadata records stable query IDs and
generated Java symbols only; it does not define editor protocol behavior or
query semantics. R16.1 does not implement source-map-backed hover/navigation.
R18.3 adds parser-ready `META-INF/mortar/source-map.json` data with format
`mortar-source-map-v1`, stable generated fixed-read anchors, and freshness
fingerprints. R18.5 makes the Rust LSP consume that artifact for VS Code hover,
copy SQL, PostgreSQL EXPLAIN code actions, and snapshot-entry navigation. R19.2
keeps that source-map contract and hardens the Java call-site recovery layer.

## Run

```bash
cd rust
cargo run -p mortar-lsp
```

The process speaks LSP over stdio and is intended to be launched by an editor
extension, not used as an interactive terminal command.
