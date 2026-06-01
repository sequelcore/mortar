import * as path from "path";
import { runTests } from "@vscode/test-electron";

async function main(): Promise<void> {
  const extensionDevelopmentPath = path.resolve(__dirname, "../../");
  const extensionTestsPath = path.resolve(__dirname, "./suite");
  const testWorkspace = path.resolve(
    extensionDevelopmentPath,
    "../../examples/editor-smoke/vscode",
  );

  await runTests({
    extensionDevelopmentPath,
    extensionTestsPath,
    launchArgs: launchArgs(testWorkspace),
  });
}

function launchArgs(testWorkspace: string): string[] {
  const args = [testWorkspace, "--disable-workspace-trust"];
  if (process.env.MORTAR_CAPTURE_SCREENSHOTS === "1") {
    args.push(
      `--remote-debugging-port=${process.env.MORTAR_VSCODE_SCREENSHOT_PORT ?? "9333"}`,
    );
  }
  return args;
}

main().catch((error: unknown) => {
  console.error(error);
  process.exit(1);
});
