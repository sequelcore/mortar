# Neovim

Mortar's editor protocol is LSP-first, so Neovim can launch `mortar-lsp`
directly over stdio.

## Example Configuration

```lua
vim.api.nvim_create_autocmd("FileType", {
  pattern = "java",
  callback = function()
    vim.lsp.start({
      name = "mortar",
      cmd = { "mortar-lsp" },
      root_dir = vim.fs.root(0, { "settings.gradle.kts", "pom.xml", ".git" }),
    })
  end,
})
```

Set the command to an absolute path when `mortar-lsp` is not on `PATH`.

```lua
cmd = { "./rust/target/debug/mortar-lsp" }
```

On Windows, append `.exe` if you point directly at local debug binaries.

The Neovim path uses the same LSP server as VS Code. Editor-specific commands
such as copying SQL or running PostgreSQL `EXPLAIN` need client-side command
bindings before they are available in Neovim.
