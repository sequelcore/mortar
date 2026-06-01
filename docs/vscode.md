# VS Code

Mortar's VS Code client lives in `editors/vscode`.

The extension launches `mortar-lsp` over stdio for Java files and registers the
`mortar.copySql` and `mortar.explainSql` commands used by LSP code actions.

## Configure

Set `mortar.lsp.path` when the `mortar-lsp` executable is not available on
`PATH`.

```json
{
  "mortar.lsp.path": "${workspaceFolder}/rust/target/debug/mortar-lsp",
  "mortar.cli.path": "${workspaceFolder}/rust/target/debug/mortar",
  "mortar.postgres.connection": "postgres://postgres@localhost:5432/postgres"
}
```

On Windows, append `.exe` if you point directly at local debug binaries.

`mortar.explainSql` sends the SQL to the Mortar CLI. The LSP server does not
own database credentials or open database connections.

## Verify

```bash
cd editors/vscode
bun install
bun run typecheck
bun run compile
bun run test
```

To include the datasource-backed `EXPLAIN` smoke, run PostgreSQL locally and set
`MORTAR_VSCODE_EXPLAIN_CONNECTION` before `bun run test`.

To regenerate editor screenshot evidence for the roadmap, run PostgreSQL
locally and execute:

```bash
MORTAR_CAPTURE_SCREENSHOTS=1 \
MORTAR_VSCODE_SCREENSHOT_PORT=9333 \
MORTAR_VSCODE_EXPLAIN_CONNECTION=postgres://postgres@localhost:55432/postgres \
bun run test:screenshots
```

The generated images are written to `docs/assets/editor-smoke`.
