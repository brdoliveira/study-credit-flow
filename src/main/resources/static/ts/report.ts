import { API_BASE, escapeHtml, renderError, request } from "./api.ts";

export function filterQuery(filters) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) if (value !== null && value !== undefined && String(value).trim()) query.set(key, String(value));
  return query.toString();
}

export function renderRows(page) {
  const rows = (page.items || []).map(item => `<tr><td>${escapeHtml(item.maskedCpf)}</td><td>${escapeHtml(item.decision)}</td><td>${escapeHtml(item.approvedAmount)}</td><td>${escapeHtml(item.evaluationId)}</td></tr>`).join("");
  return `<p>${page.total || 0} avaliação(ões) encontrada(s).</p><div class="table-responsive"><table class="table"><thead><tr><th>Cliente</th><th>Decisão</th><th>Valor</th><th>Identificador</th></tr></thead><tbody>${rows}</tbody></table></div>`;
}

if (typeof document !== "undefined") {
  const form = document.querySelector("#report-filters");
  const feedback = document.querySelector("#feedback");
  const results = document.querySelector("#report-results");
  const filters = () => Object.fromEntries(new FormData(form));
  async function list() { const response = await request(`?${filterQuery(filters())}`); results.innerHTML = renderRows(await response.json()); }
  form.addEventListener("submit", async event => { event.preventDefault(); feedback.innerHTML = ""; try { await list(); } catch (error) { feedback.innerHTML = renderError(error); } });
  document.querySelector("#download-pdf").addEventListener("click", () => { window.location.assign(`${API_BASE}/report.pdf?${filterQuery(filters())}`); });
}

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test"); const assert = await import("node:assert/strict");
  test("@spec:AC-042 reutiliza exatamente os filtros na consulta e no PDF", () => {
    const query = filterQuery({ decision: "APPROVED", from: "2026-08-01", to: "2026-08-15" });
    assert.equal(query, "decision=APPROVED&from=2026-08-01&to=2026-08-15");
    assert.equal(`${API_BASE}/report.pdf?${query}`, "/api/v1/credit-evaluations/report.pdf?decision=APPROVED&from=2026-08-01&to=2026-08-15");
  });
}
