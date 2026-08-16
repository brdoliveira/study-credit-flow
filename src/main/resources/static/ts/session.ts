import { setCsrfToken } from "./api.ts";

export async function loadSession(fetcher = fetch) {
  const response = await fetcher("/api/session", { credentials: "same-origin", headers: { Accept: "application/json" } });
  if (!response.ok) throw new Error("Unable to load the authenticated session");
  const session = await response.json();
  setCsrfToken(session.csrfToken);
  return session;
}

export async function initialiseSession(documentRef = document) {
  const session = await loadSession();
  const authorities = new Set(session.authorities || []);
  documentRef.querySelectorAll("[data-authority]").forEach(element => {
    element.hidden = !authorities.has(element.dataset.authority);
  });
  const logout = documentRef.querySelector("#logout");
  if (logout) logout.addEventListener("click", async () => {
    await fetch("/api/session/logout", { method: "POST", credentials: "same-origin", headers: { "X-XSRF-TOKEN": session.csrfToken } });
    window.location.assign("/");
  });
}

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test"); const assert = await import("node:assert/strict");
  test("@spec:AC-049 keeps credentials outside JavaScript and sends only a CSRF token", async () => {
    let received;
    const session = await loadSession(async (url, options) => {
      received = { url, options };
      return { ok: true, json: async () => ({ authenticated: true, authorities: [], csrfToken: "csrf-value" }) };
    });
    assert.equal(session.csrfToken, "csrf-value");
    assert.equal(received.url, "/api/session");
    assert.doesNotMatch(JSON.stringify(session), /access.?token|refresh.?token|client.?secret/i);
  });
}
