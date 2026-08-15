// cPanel/Passenger Node.js App entry point. Delegates to the actual
// production server (`vinext start`), which reads PORT from the
// environment that Passenger provides.
const { spawn } = require("node:child_process");
const path = require("node:path");

const child = spawn(
  process.execPath,
  [path.join(__dirname, "node_modules", "vinext", "dist", "cli.js"), "start"],
  { cwd: __dirname, stdio: "inherit", env: process.env }
);

child.on("exit", (code) => process.exit(code ?? 0));
