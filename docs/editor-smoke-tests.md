# Editor Smoke Tests

This document is the canonical smoke matrix for Mortar editor tooling.

R11.8 is complete for the primary supported editor path. VS Code is the primary
editor target because it exercises the editor-neutral LSP path with a widely
used Java workflow.

## VS Code

Required setup:

- `cargo build -p mortar-lsp`
- `cargo build -p mortar-cli`
- `cd editors/vscode && bun install && bun run compile`
- Open `examples/editor-smoke/vscode` with the Mortar extension development
  host.

Matrix:

| Check | Expected Result | Evidence |
| --- | --- | --- |
| Extension activates for Java file | Mortar extension loads in an isolated VS Code extension host | Passed on 2026-06-01 with `bun run test` in `editors/vscode` against VS Code 1.122.1 |
| LSP starts for Java file | Mortar LSP initializes without extension host errors | Passed on 2026-06-01 with `bun run test`; extension activation awaits `LanguageClient.start()` using the smoke workspace `mortar.lsp.path` |
| SQL hover | Hover over mapped query shows fenced SQL block | Passed on 2026-06-01 with `vscode.executeHoverProvider` in `bun run test` |
| Code actions | VS Code returns and displays Mortar code actions for mapped query | Passed on 2026-06-01 with `vscode.executeCodeActionProvider` in `bun run test`; visual menu evidence captured in `docs/assets/editor-smoke/code-actions.png` |
| Copy SQL action | `Mortar: Copy Generated SQL` command is contributed and writes generated SQL to clipboard | Passed on 2026-06-01 with `bun run test` in `editors/vscode` |
| EXPLAIN action | `Run PostgreSQL EXPLAIN` command is contributed and writes plan to Mortar output channel using configured datasource | Passed on 2026-06-01 with `MORTAR_VSCODE_EXPLAIN_CONNECTION=postgres://postgres@localhost:55432/postgres bun run test` against Docker PostgreSQL 16; command returned a plan containing `Result` |
| Workspace configuration | Extension reads smoke workspace `mortar.lsp.path` and `mortar.cli.path` settings | Passed on 2026-06-01 with `bun run test` in `editors/vscode` |
| Diagnostics | Mortar diagnostics appear in VS Code for broken snapshot mapping | Passed on 2026-06-01 with `vscode.languages.getDiagnostics` in `bun run test`; schema-drift diagnostics with live DB remain pending |

Screenshot requirements:

- hover SQL result: `docs/assets/editor-smoke/hover-sql.png`;
- copy SQL action menu: `docs/assets/editor-smoke/code-actions.png`;
- EXPLAIN output channel: `docs/assets/editor-smoke/explain-output.png`;
- diagnostics in Problems view: `docs/assets/editor-smoke/diagnostics-problems.png`.

Screenshot evidence was generated on 2026-06-01 with:

```bash
MORTAR_CAPTURE_SCREENSHOTS=1 \
MORTAR_VSCODE_SCREENSHOT_PORT=9333 \
MORTAR_VSCODE_EXPLAIN_CONNECTION=postgres://postgres@localhost:55432/postgres \
bun run test:screenshots
```

## Neovim

Required setup:

- `cargo build -p mortar-lsp`
- launch `mortar-lsp` using the configuration in `docs/neovim.md`.

Matrix:

| Check | Expected Result | Evidence |
| --- | --- | --- |
| LSP starts for Java file | Neovim reports `mortar` as an active LSP client | Pending |
| Hover request reaches server | Hover request returns a response when source mapping is available | Pending |
