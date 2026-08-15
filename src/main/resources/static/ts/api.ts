export const API_BASE = "/api/v1/credit-evaluations";

export function userMessage(error) {
  const status = Number(error?.status);
  if (status === 400) return "Revise os dados informados e tente novamente.";
  if (status === 401) return "Sua sessão não é válida. Faça a autenticação novamente.";
  if (status === 403) return "Você não possui permissão para esta operação.";
  if (status === 404) return "O recurso solicitado não foi encontrado.";
  return "Não foi possível concluir a operação agora. Tente novamente mais tarde.";
}

export function correlationId(error) {
  return typeof error?.correlationId === "string" && error.correlationId.trim() ? error.correlationId : null;
}

export async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, { headers: { Accept: "application/json", ...options.headers }, ...options });
  if (response.ok) return response;
  let error = { status: response.status };
  try { error = { ...error, ...await response.json() }; } catch { /* The message remains generic. */ }
  throw error;
}

export function renderError(error) {
  const correlation = correlationId(error);
  return `<div class="alert alert-danger" role="alert"><p class="mb-0">${userMessage(error)}</p>${correlation ? `<small class="correlation-id">Código de acompanhamento: ${escapeHtml(correlation)}</small>` : ""}</div>`;
}

export function escapeHtml(value) { return String(value).replace(/[&<>'"]/g, character => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" }[character])); }

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test"); const assert = await import("node:assert/strict");
  test("@spec:AC-043 apresenta erro compreensível sem vazar detalhes internos", () => {
    const html = renderError({ status: 500, correlationId: "corr-42", message: "java.sql.SQLException: secret stack trace" });
    assert.match(html, /Não foi possível concluir a operação agora/);
    assert.match(html, /corr-42/);
    assert.doesNotMatch(html, /SQLException|stack trace/);
  });
}
