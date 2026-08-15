// cPanel/Passenger (LiteSpeed lsnode.js) Node.js App entry point.
//
// LiteSpeed's lsnode.js patches the `http` module in this same process
// before requiring this file, so any http.Server created here and told to
// .listen() gets transparently redirected to the LSAPI Unix socket it
// manages. Spawning `vinext start` as a separate child process (the naive
// approach) does NOT work: the child gets its own unpatched `http` module,
// so LiteSpeed never sees it and the request never reaches it.
//
// So we run vinext's CLI in-process instead of as a subprocess, by faking
// argv the way `vinext start` would see it.
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
process.chdir(__dirname);
process.argv = [process.argv[0], process.argv[1], "start"];

import(path.join(__dirname, "node_modules", "vinext", "dist", "cli.js")).catch(
  (e) => {
    console.error(e);
    process.exit(1);
  },
);
