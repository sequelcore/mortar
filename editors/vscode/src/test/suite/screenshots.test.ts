import * as assert from "assert";
import * as fs from "fs";
import * as path from "path";
import WebSocket from "ws";
import * as vscode from "vscode";

suite("Mortar VS Code screenshots", () => {
  test("captures hover, code actions, diagnostics, and EXPLAIN output", async function () {
    this.timeout(45_000);

    const workspace = smokeWorkspace();
    const clientUri = vscode.Uri.file(
      path.join(
        workspace,
        "src",
        "main",
        "java",
        "example",
        "ClientRepository.java",
      ),
    );
    const clientDocument = await vscode.workspace.openTextDocument(clientUri);
    const editor = await vscode.window.showTextDocument(clientDocument);
    await focusEditorOnly();
    editor.selection = new vscode.Selection(4, 16, 4, 16);
    await delay(500);
    await vscode.commands.executeCommand("editor.action.showHover");
    await delay(750);
    await captureWorkbenchScreenshot("hover-sql.png");

    await vscode.commands.executeCommand("editor.action.hideHover");
    await focusEditorOnly();
    await delay(250);
    await vscode.commands.executeCommand("editor.action.codeAction", {
      apply: "never",
      kind: "source",
    });
    await delay(750);
    await captureWorkbenchScreenshot("code-actions.png");
    await vscode.commands.executeCommand("workbench.action.closeQuickOpen");

    const missingUri = vscode.Uri.file(
      path.join(
        workspace,
        "src",
        "main",
        "java",
        "example",
        "MissingSnapshotRepository.java",
      ),
    );
    await vscode.window.showTextDocument(await vscode.workspace.openTextDocument(missingUri));
    await waitFor(
      () => vscode.languages.getDiagnostics(missingUri),
      (diagnostics) => diagnostics.length > 0,
    );
    await vscode.commands.executeCommand("workbench.actions.view.problems");
    await delay(750);
    await captureWorkbenchScreenshot("diagnostics-problems.png");

    const connection = process.env.MORTAR_VSCODE_EXPLAIN_CONNECTION;
    assert.ok(connection, "MORTAR_VSCODE_EXPLAIN_CONNECTION is required for screenshots");
    const configuration = vscode.workspace.getConfiguration("mortar");
    await configuration.update(
      "postgres.connection",
      connection,
      vscode.ConfigurationTarget.Workspace,
    );
    const result = await vscode.commands.executeCommand<{ ok: boolean; output: string }>(
      "mortar.explainSql",
      "select 1",
    );
    assert.equal(result.ok, true);
    await delay(750);
    await captureWorkbenchScreenshot("explain-output.png");
  });
});

function smokeWorkspace(): string {
  const folder = vscode.workspace.workspaceFolders?.[0];
  assert.ok(folder, "Smoke workspace should be open");
  return folder.uri.fsPath;
}

async function captureWorkbenchScreenshot(fileName: string): Promise<void> {
  const client = await connectToWorkbench();
  try {
    await client.send("Page.bringToFront");
    await client.send("Page.enable");
    const screenshot = await client.send("Page.captureScreenshot", {
      captureBeyondViewport: false,
      format: "png",
    });
    const outputDir = path.resolve(smokeWorkspace(), "../../../docs/assets/editor-smoke");
    fs.mkdirSync(outputDir, { recursive: true });
    fs.writeFileSync(
      path.join(outputDir, fileName),
      Buffer.from(screenshot.data as string, "base64"),
    );
  } finally {
    client.close();
  }
}

async function focusEditorOnly(): Promise<void> {
  await executeCommandIfAvailable("workbench.action.closePanel");
  await executeCommandIfAvailable("workbench.action.closeAuxiliaryBar");
  await vscode.commands.executeCommand("workbench.action.focusActiveEditorGroup");
}

async function executeCommandIfAvailable(command: string): Promise<void> {
  const commands = await vscode.commands.getCommands(true);
  if (commands.includes(command)) {
    await vscode.commands.executeCommand(command);
  }
}

async function connectToWorkbench(): Promise<CdpClient> {
  const port = process.env.MORTAR_VSCODE_SCREENSHOT_PORT ?? "9333";
  const targets = (await fetchJson(
    `http://127.0.0.1:${port}/json/list`,
  )) as Array<{ title: string; type: string; webSocketDebuggerUrl: string }>;
  const target =
    targets.find((candidate) => candidate.type === "page" && candidate.title.includes("Visual Studio Code")) ??
    targets.find((candidate) => candidate.type === "page");
  assert.ok(target, "VS Code workbench CDP target should be available");
  return CdpClient.connect(target.webSocketDebuggerUrl);
}

async function fetchJson(url: string): Promise<unknown> {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`CDP request failed: ${response.status} ${response.statusText}`);
  }
  return response.json();
}

class CdpClient {
  private nextId = 1;
  private readonly pending = new Map<number, (value: unknown) => void>();

  private constructor(private readonly socket: WebSocket) {
    socket.on("message", (message) => {
      const payload = JSON.parse(message.toString()) as { id?: number; result?: unknown };
      if (payload.id !== undefined) {
        this.pending.get(payload.id)?.(payload.result);
        this.pending.delete(payload.id);
      }
    });
  }

  static connect(url: string): Promise<CdpClient> {
    return new Promise((resolve, reject) => {
      const socket = new WebSocket(url);
      socket.once("open", () => resolve(new CdpClient(socket)));
      socket.once("error", reject);
    });
  }

  send(method: string, params?: Record<string, unknown>): Promise<Record<string, unknown>> {
    const id = this.nextId;
    this.nextId += 1;
    return new Promise((resolve) => {
      this.pending.set(id, (value) => resolve((value ?? {}) as Record<string, unknown>));
      this.socket.send(JSON.stringify({ id, method, params }));
    });
  }

  close(): void {
    this.socket.close();
  }
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

  throw new Error("Timed out waiting for VS Code screenshot condition");
}

function delay(milliseconds: number): Promise<void> {
  return new Promise((resolve) => {
    setTimeout(resolve, milliseconds);
  });
}
