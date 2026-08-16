// Some hosts (notably our relay VPS) advertise IPv6 connectivity that isn't
// actually routable, causing Node's global fetch() (undici) to hang/fail
// with "fetch failed" while racing a dead-end IPv6 address alongside IPv4.
// `--dns-result-order=ipv4first` does NOT fix this: undici has its own
// connection logic independent of Node's dns module ordering. Forcing the
// global dispatcher to IPv4-only connections is what actually works.
//
// Loaded via `node --import ./scripts/preload-force-ipv4.mjs` (see
// systemd unit / NODE_OPTIONS on the relay VPS).
import { Agent, setGlobalDispatcher } from "undici";

setGlobalDispatcher(new Agent({ connect: { family: 4 } }));
