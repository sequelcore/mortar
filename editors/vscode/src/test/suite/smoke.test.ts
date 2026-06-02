import * as assert from "assert";
import * as path from "path";
import * as vscode from "vscode";

suite("Mortar VS Code extension smoke", () => {
  test("activates for Java files and contributes commands", async () => {
    const document = await vscode.workspace.openTextDocument({
      content: "package example;\npublic final class Smoke {}\n",
      language: "java",
    });
    await vscode.window.showTextDocument(document);

    const extension = vscode.extensions.getExtension("sequel.mortar-vscode");
    assert.ok(extension, "Mortar extension should be installed in extension host");
    await extension.activate();
    assert.ok(extension.isActive, "Mortar extension should activate");

    const commands = await vscode.commands.getCommands(true);
    assert.ok(commands.includes("mortar.copySql"));
    assert.ok(commands.includes("mortar.explainSql"));
  });

  test("uses smoke workspace configuration", () => {
    const configuration = vscode.workspace.getConfiguration("mortar");

    assert.match(
      configuration.get<string>("lsp.path", ""),
      /mortar-lsp\.exe$/,
    );
    assert.match(configuration.get<string>("cli.path", ""), /mortar\.exe$/);
  });

  test("returns SQL hover and code actions from the LSP server", async () => {
    const workspace = smokeWorkspace();
    const uri = vscode.Uri.file(
      path.join(
        workspace,
        "src",
        "main",
        "java",
        "example",
        "ClientRepository.java",
      ),
    );
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);
    await delay(500);

    const position = new vscode.Position(6, 40);
    const hovers = await vscode.commands.executeCommand<vscode.Hover[]>(
      "vscode.executeHoverProvider",
      uri,
      position,
    );
    assert.ok(
      hovers.some((hover) =>
        hover.contents.some((content) =>
          contentValue(content).includes(
            "select c.id from clients c where c.id = ?",
          ),
        ),
      ),
      "SQL hover should include generated SQL",
    );

    const actions = await vscode.commands.executeCommand<
      (vscode.CodeAction | vscode.Command)[]
    >("vscode.executeCodeActionProvider", uri, new vscode.Range(position, position));
    const titles = actions.map((action) => action.title);
    assert.ok(titles.includes("Copy generated SQL"));
    assert.ok(titles.includes("Run PostgreSQL EXPLAIN"));

    const definitions = await vscode.commands.executeCommand<vscode.Location[]>(
      "vscode.executeDefinitionProvider",
      uri,
      position,
    );
    assert.ok(
      definitions.some((definition) => {
        return (
          definition.uri.fsPath.endsWith("mortar.sql.snap.json") &&
          definition.range.start.line === 4
        );
      }),
      "Definition should navigate to the matching SQL snapshot entry",
    );
  });

  test("publishes diagnostics for missing snapshot markers", async () => {
    const workspace = smokeWorkspace();
    const missingFile = path.join(
      workspace,
      "src",
      "main",
      "java",
      "example",
      "MissingSnapshotRepository.java",
    );
    const uri = vscode.Uri.file(missingFile);
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);

    const diagnostics = await waitFor(
      () => vscode.languages.getDiagnostics(uri),
      (diagnostics) => diagnostics.length > 0,
    );

    assert.equal(diagnostics[0].source, "mortar");
    assert.match(
      diagnostics[0].message,
      /Mortar SQL snapshot was not found: MissingSnapshotRepository\.find/,
    );
  });

  test("copy SQL command writes to clipboard", async () => {
    const sql = "select c.id from clients c where c.id = ?";

    await vscode.commands.executeCommand("mortar.copySql", sql);

    assert.equal(await vscode.env.clipboard.readText(), sql);
  });

  test("runs EXPLAIN against configured PostgreSQL datasource", async function () {
    const connection = process.env.MORTAR_VSCODE_EXPLAIN_CONNECTION;
    if (connection === undefined || connection.trim() === "") {
      this.skip();
    }

    const configuration = vscode.workspace.getConfiguration("mortar");
    const previousConnection = configuration.get<string>("postgres.connection", "");
    await configuration.update(
      "postgres.connection",
      connection,
      vscode.ConfigurationTarget.Workspace,
    );

    try {
      const result = await vscode.commands.executeCommand<{
        ok: boolean;
        output: string;
      }>("mortar.explainSql", "select 1");

      assert.equal(result.ok, true);
      assert.match(result.output, /Result/);
    } finally {
      await configuration.update(
        "postgres.connection",
        previousConnection,
        vscode.ConfigurationTarget.Workspace,
      );
    }
  });
});

function smokeWorkspace(): string {
  const folder = vscode.workspace.workspaceFolders?.[0];
  assert.ok(folder, "Smoke workspace should be open");
  return folder.uri.fsPath;
}

function contentValue(content: vscode.MarkdownString | vscode.MarkedString): string {
  if (typeof content === "string") {
    return content;
  }
  const candidate = content as { value?: unknown };
  if (typeof candidate.value === "string") {
    return candidate.value;
  }
  return JSON.stringify(content);
}

async function waitFor<T>(
  read: () => T,
  isReady: (value: T) => boolean,
): Promise<T> {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const value = read();
    if (isReady(value)) {
      return value;
    }
    await delay(250);
  }

  throw new Error("Timed out waiting for VS Code smoke condition");
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}
