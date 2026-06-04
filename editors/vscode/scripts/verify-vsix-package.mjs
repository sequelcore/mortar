import { execFileSync } from "node:child_process";
import { statSync } from "node:fs";

const expectedFiles = [
  "README.md",
  "package.json",
  "LICENSE",
  "CHANGELOG.md",
  "dist/extension.js",
];
const expected = new Set(expectedFiles);
const deniedPatterns = [
  /^src\//,
  /^dist\/test\//,
  /^node_modules\//,
  /^build\//,
  /^\.vscode-test\//,
  /^bun\.lock$/,
  /^tsconfig\.json$/,
];
const maxBundleBytes = 400 * 1024;
const listedCommand =
  process.platform === "win32"
    ? ["cmd.exe", ["/d", "/s", "/c", "vsce ls"]]
    : ["vsce", ["ls"]];
const listed = execFileSync(listedCommand[0], listedCommand[1], {
  encoding: "utf8",
  stdio: ["ignore", "pipe", "inherit"],
})
  .split(/\r?\n/)
  .map((line) => line.trim())
  .filter((line) => line.length > 0);
const extraFiles = listed.filter((file) => !expected.has(file));
const missingFiles = expectedFiles.filter((file) => !listed.includes(file));
const deniedFiles = listed.filter((file) => deniedPatterns.some((pattern) => pattern.test(file)));
const bundleSize = statSync("dist/extension.js").size;
const failures = [];

if (missingFiles.length > 0) {
  failures.push(`Missing expected VSIX files: ${missingFiles.join(", ")}`);
}
if (extraFiles.length > 0) {
  failures.push(`Unexpected VSIX files: ${extraFiles.join(", ")}`);
}
if (deniedFiles.length > 0) {
  failures.push(`Denied VSIX files: ${deniedFiles.join(", ")}`);
}
if (bundleSize > maxBundleBytes) {
  failures.push(
    `dist/extension.js is ${bundleSize} bytes, above the ${maxBundleBytes} byte budget`,
  );
}

if (failures.length > 0) {
  console.error(failures.join("\n"));
  process.exit(1);
}

console.log(
  `VSIX package contract ok: ${listed.length} files, dist/extension.js ${bundleSize} bytes`,
);
