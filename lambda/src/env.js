import { readFileSync } from "node:fs";
import { resolve } from "node:path";

function loadDotEnvFile(filePath) {
  let content = "";
  try {
    content = readFileSync(filePath, "utf-8");
  } catch {
    return;
  }

  for (const rawLine of content.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;

    const idx = line.indexOf("=");
    if (idx <= 0) continue;

    const key = line.slice(0, idx).trim();
    if (!key || process.env[key]) continue;

    let value = line.slice(idx + 1).trim();
    if (
      (value.startsWith("\"") && value.endsWith("\"")) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    process.env[key] = value;
  }
}

const lambdaRoot = resolve(process.cwd(), "lambda");
loadDotEnvFile(resolve(process.cwd(), ".env"));
loadDotEnvFile(resolve(lambdaRoot, ".env"));
