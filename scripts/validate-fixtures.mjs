import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const fixturesDir = path.join(root, "shared", "fixtures");

const files = (await readdir(fixturesDir)).filter((f) => f.endsWith(".json")).sort();
let failed = false;

for (const file of files) {
  const full = path.join(fixturesDir, file);
  try {
    JSON.parse(await readFile(full, "utf8"));
    console.log(`OK ${file}`);
  } catch (err) {
    failed = true;
    console.error(`FAIL ${file}: ${err.message}`);
  }
}

if (files.length !== 10) {
  failed = true;
  console.error(`Expected 10 fixture JSON files, found ${files.length}`);
}

process.exit(failed ? 1 : 0);
