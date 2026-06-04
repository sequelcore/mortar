# Mortar LSP

Mortar's Language Server Protocol server lives in `rust/crates/mortar-lsp`.
Editor tooling is optional; Java applications do not depend on the LSP at
runtime.

## Current Scope

The server currently supports:

- full text document sync;
- hover for generated fixed-read SQL;
- code actions for copy SQL and PostgreSQL EXPLAIN;
- definition support from generated read call sites to SQL snapshot entries;
- diagnostics for stale metadata, missing snapshots, unsupported alias syntax,
  ambiguous aliases, reassigned aliases, and malformed Java buffers.

The server reads generated metadata and source maps from `META-INF/mortar` and
reads SQL from `mortar.sql.snap.json`. It does not render SQL in the editor.

## Supported Generated Read Calls

The LSP resolves generated fixed-read calls such as:

```java
QClient.CLIENT.read(renderer).findById(id);
QClient.CLIENT.read(renderer).findAll();
```

It also supports two same-file local alias shapes:

```java
var client = QClient.CLIENT;
client.read(renderer).findById(id);

var read = QClient.CLIENT.read(renderer);
read.findById(id);
```

The resolver uses local Java syntax plus fresh `mortar-metadata-v1` and
`mortar-source-map-v1` artifacts. Query identity comes from generated metamodel
context and generated read member, not from method name alone.

## Fail-Closed Behavior

When metadata, source-map, snapshot evidence, or generated metamodel context is
missing, stale, ambiguous, unsupported, or unparseable, the LSP returns no
hover SQL, no copy SQL action, no EXPLAIN action, and no definition target. It
then reports a reason-specific diagnostic.

Unsupported scope:

- full Java type binding;
- classpath-aware semantic resolution;
- helper-returned receiver support;
- alias chaining;
- aliases proven only by type annotations;
- field aliases;
- cross-method or cross-file aliases;
- wildcard static import success;
- arbitrary DSL call-site analysis;
- editor-side SQL rendering.

Explicit snapshot markers remain a manual path:

```java
// mortar:snapshot=ClientRepository.findById
```

Markers are not a fallback for stale generated fixed-read source-map data.

## Run

```bash
cd rust
cargo run -p sequel-mortar-lsp
```

The process speaks LSP over stdio and is intended to be launched by an editor
extension.
