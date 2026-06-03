import * as assert from "assert";
import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

interface LatencyTraceEntry {
  scenario: string;
  operation: string;
  elapsedMilliseconds: number;
}

const latencyTrace: LatencyTraceEntry[] = [];

suite("Mortar VS Code extension smoke", () => {
  suiteTeardown(() => {
    writeLatencyTrace();
  });

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

    await assertGeneratedReadEditorFeatures(
      uri,
      document,
      "readCanonical",
      "QClient.CLIENT.read",
    );
    await assertGeneratedReadEditorFeatures(
      uri,
      document,
      "readMetamodelAlias",
      "client.read",
    );
    await assertGeneratedReadEditorFeatures(
      uri,
      document,
      "readNamespaceAlias",
      "read.findById",
    );
  });

  test("publishes diagnostics for unsupported generated alias shapes", async () => {
    const workspace = smokeWorkspace();
    const unsupportedFile = path.join(
      workspace,
      "src",
      "main",
      "java",
      "example",
      "UnsupportedAliasRepository.java",
    );
    const uri = vscode.Uri.file(unsupportedFile);
    const document = await vscode.workspace.openTextDocument(uri);
    await vscode.window.showTextDocument(document);

    const diagnostics = await measureLatency(
      "unsupportedAliasDiagnostics",
      "diagnostics-published",
      () =>
        waitFor(
          () => vscode.languages.getDiagnostics(uri),
          (diagnostics) => diagnostics.length > 0,
        ),
    );

    assert.equal(diagnostics[0].source, "mortar");
    assert.equal(diagnostics[0].code, "mortar-alias-unsupported");
    assert.match(
      diagnostics[0].message,
      /Mortar generated call uses unsupported alias syntax/,
    );
  });

  async function assertGeneratedReadEditorFeatures(
    uri: vscode.Uri,
    document: vscode.TextDocument,
    methodName: string,
    needle: string,
  ): Promise<void> {
    const position = positionOfAfter(document, methodName, needle);
    const hovers = await measureLatency(
      methodName,
      "hover-provider",
      () =>
        vscode.commands.executeCommand<vscode.Hover[]>(
          "vscode.executeHoverProvider",
          uri,
          position,
        ),
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

    const actions = await measureLatency(
      methodName,
      "code-action-provider",
      () =>
        vscode.commands.executeCommand<(vscode.CodeAction | vscode.Command)[]>(
          "vscode.executeCodeActionProvider",
          uri,
          new vscode.Range(position, position),
        ),
    );
    const titles = actions.map((action) => action.title);
    assert.ok(titles.includes("Copy generated SQL"));
    assert.ok(titles.includes("Run PostgreSQL EXPLAIN"));

    const definitions = await measureLatency(
      methodName,
      "definition-provider",
      () =>
        vscode.commands.executeCommand<vscode.Location[]>(
          "vscode.executeDefinitionProvider",
          uri,
          position,
        ),
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
  }

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

    const diagnostics = await measureLatency(
      "missingSnapshotDiagnostics",
      "diagnostics-published",
      () =>
        waitFor(
          () => vscode.languages.getDiagnostics(uri),
          (diagnostics) => diagnostics.length > 0,
        ),
    );

    assert.equal(diagnostics[0].source, "mortar");
    assert.match(
      diagnostics[0].message,
      /Mortar SQL snapshot was not found: MissingSnapshotRepository\.find/,
    );
  });

  test("copy SQL command writes to clipboard", async () => {
    const sql = "select c.id from clients c where c.id = ?";

    await measureLatency("copySql", "command", () =>
      vscode.commands.executeCommand("mortar.copySql", sql),
    );

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
      const result = await measureLatency("explainSql", "command", () =>
        vscode.commands.executeCommand<{
          ok: boolean;
          output: string;
        }>("mortar.explainSql", "select 1"),
      );

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

function positionOfAfter(
  document: vscode.TextDocument,
  anchor: string,
  needle: string,
): vscode.Position {
  const text = document.getText();
  const anchorOffset = text.indexOf(anchor);
  assert.notEqual(anchorOffset, -1, `${anchor} should exist in fixture`);
  const offset = text.indexOf(needle, anchorOffset);
  assert.notEqual(offset, -1, `${needle} should exist after ${anchor}`);
  return document.positionAt(offset);
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

async function measureLatency<T>(
  scenario: string,
  operation: string,
  action: () => PromiseLike<T>,
): Promise<T> {
  const startedAt = Date.now();
  try {
    return await action();
  } finally {
    if (process.env.MORTAR_VSCODE_LATENCY_TRACE !== undefined) {
      latencyTrace.push({
        scenario,
        operation,
        elapsedMilliseconds: Date.now() - startedAt,
      });
    }
  }
}

function writeLatencyTrace(): void {
  const tracePath = process.env.MORTAR_VSCODE_LATENCY_TRACE;
  if (tracePath === undefined || tracePath.trim() === "") {
    return;
  }

  fs.mkdirSync(path.dirname(tracePath), { recursive: true });
  fs.writeFileSync(
    tracePath,
    JSON.stringify(
      {
        schema: "mortar-r23-vscode-editor-latency-trace-v1",
        entries: latencyTrace,
      },
      null,
      2,
    ) + "\n",
    "utf-8",
  );
}
