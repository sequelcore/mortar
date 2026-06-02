import { execFile } from "child_process";
import { promisify } from "util";
import * as vscode from "vscode";
import {
  LanguageClient,
  LanguageClientOptions,
  ServerOptions,
  TransportKind,
} from "vscode-languageclient/node";

let client: LanguageClient | undefined;
const execFileAsync = promisify(execFile);

export interface ExplainSqlResult {
  ok: boolean;
  output: string;
}

export async function activate(context: vscode.ExtensionContext): Promise<void> {
  const configuration = vscode.workspace.getConfiguration("mortar");
  const command = resolveWorkspacePath(configuration.get<string>("lsp.path", "mortar-lsp"));
  const outputChannel = vscode.window.createOutputChannel("Mortar");
  const serverOptions: ServerOptions = {
    command,
    transport: TransportKind.stdio,
  };
  const clientOptions: LanguageClientOptions = {
    documentSelector: [{ language: "java", scheme: "file" }],
  };

  client = new LanguageClient(
    "mortar",
    "Mortar",
    serverOptions,
    clientOptions,
  );

  context.subscriptions.push(
    vscode.commands.registerCommand("mortar.copySql", async (sql: string) => {
      await vscode.env.clipboard.writeText(sql);
    }),
    vscode.commands.registerCommand("mortar.explainSql", (sql: string) => {
      return explainSql(sql, outputChannel);
    }),
    outputChannel,
    client,
  );

  await client.start();
}

export async function deactivate(): Promise<void> {
  if (client === undefined) {
    return;
  }

  await client.stop();
  client = undefined;
}

async function explainSql(
  sql: string,
  outputChannel: vscode.OutputChannel,
): Promise<ExplainSqlResult> {
  const configuration = vscode.workspace.getConfiguration("mortar");
  const cliPath = resolveWorkspacePath(configuration.get<string>("cli.path", "mortar"));
  const connection = configuration.get<string>("postgres.connection", "");

  if (connection.trim() === "") {
    const message = "Set mortar.postgres.connection before running PostgreSQL EXPLAIN.";
    void vscode.window.showErrorMessage(
      message,
    );
    return { ok: false, output: message };
  }

  outputChannel.clear();
  outputChannel.appendLine("Running PostgreSQL EXPLAIN with Mortar...");

  try {
    const { stdout, stderr } = await execFileAsync(
      cliPath,
      ["explain", "--connection", connection, "--sql", sql],
      {
        windowsHide: true,
        maxBuffer: 1024 * 1024,
      },
    );
    appendIfPresent(outputChannel, stdout);
    appendIfPresent(outputChannel, stderr);
    outputChannel.show(true);
    return {
      ok: true,
      output: [stdout, stderr]
        .filter((value) => value.trim() !== "")
        .map((value) => value.trimEnd())
        .join("\n"),
    };
  } catch (error) {
    const output = errorOutput(error);
    appendIfPresent(outputChannel, output);
    outputChannel.show(true);
    void vscode.window.showErrorMessage(
      "Mortar PostgreSQL EXPLAIN failed. See the Mortar output for details.",
    );
    return { ok: false, output };
  }
}

function appendIfPresent(
  outputChannel: vscode.OutputChannel,
  value: string | undefined,
): void {
  if (value !== undefined && value.trim() !== "") {
    outputChannel.appendLine(value.trimEnd());
  }
}

function resolveWorkspacePath(value: string): string {
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
  if (workspaceFolder === undefined) {
    return value;
  }

  return value.replace(/\$\{workspaceFolder\}/g, workspaceFolder.uri.fsPath);
}

function errorOutput(error: unknown): string {
  if (typeof error === "object" && error !== null) {
    const output = error as { stderr?: string; stdout?: string };
    const parts = [output.stdout, output.stderr].filter(
      (value): value is string => value !== undefined && value.trim() !== "",
    );
    if (parts.length > 0) {
      return parts.join("\n");
    }
  }

  return "Mortar CLI failed without output.";
}
