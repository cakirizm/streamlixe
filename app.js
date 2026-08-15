// cPanel/Passenger Node.js App entry point. Delegates to the actual
// production server (`vinext start`), which reads PORT from the
// environment that Passenger provides.
import { spawn } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const child = spawn(
  process.execPath,
  [path.join(__dirname, "node_modules", "vinext", "dist", "cli.js"), "start"],
  { cwd: __dirname, stdio: "inherit", env: process.env }
);

child.on("exit", (code) => process.exit(code ?? 0));
