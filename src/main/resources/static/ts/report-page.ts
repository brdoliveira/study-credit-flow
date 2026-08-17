import { initialiseSession } from "./session.ts";

if (typeof document !== "undefined") {
  await initialiseSession();
  globalThis.lucide?.createIcons();
  await import("./report.ts");
}
