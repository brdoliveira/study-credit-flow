import { API_BASE, escapeHtml, renderError, request } from "./api.ts";

export function evaluationPayload(form) {
  const data = new FormData(form);
  return {
    name: data.get("name"), cpf: data.get("cpf"), creditScore: Number(data.get("creditScore")),
    currentInvoiceAmount: Number(data.get("currentInvoiceAmount")), totalLimit: Number(data.get("totalLimit")),
    availableLimit: Number(data.get("availableLimit")), latePayments: Number(data.get("latePayments")),
    monthlySpending: String(data.get("monthlySpending")).split(",").map(value => Number(value.trim()))
  };
}

export function renderDecision(result) {
  const approved = result.decision === "APPROVED";
  const rules = (result.rules || []).map(rule => `<li class="list-group-item"><strong>${escapeHtml(rule.name)}</strong> <span class="badge text-bg-secondary rule-status">${escapeHtml(rule.status)}</span><br><small>${escapeHtml(rule.reason)}</small></li>`).join("");
  return `<article class="card shadow-sm decision-${approved ? "approved" : "rejected"}"><div class="card-body"><h2 class="h4">${approved ? "Crédito aprovado" : "Crédito reprovado"}</h2><p><strong>Valor aprovado:</strong> ${new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(result.approvedAmount || 0)}</p><p><strong>Identificador da avaliação:</strong> ${escapeHtml(result.evaluationId)}</p><h3 class="h5">Regras avaliadas</h3><ul class="list-group">${rules}</ul></div></article>`;
}

if (typeof document !== "undefined") {
  const form = document.querySelector("#evaluation-form");
  const feedback = document.querySelector("#feedback");
  const decision = document.querySelector("#decision");
  form.addEventListener("submit", async event => {
    event.preventDefault(); feedback.innerHTML = ""; decision.innerHTML = "";
    try {
      const response = await request("", { method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() }, body: JSON.stringify(evaluationPayload(form)) });
      decision.innerHTML = renderDecision(await response.json());
    } catch (error) { feedback.innerHTML = renderError(error); }
  });
}

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test"); const assert = await import("node:assert/strict");
  test("@spec:AC-041 mostra decisão, valor, regras, motivos e identificador", () => {
    const html = renderDecision({ decision: "APPROVED", approvedAmount: 700, evaluationId: "eval-1", rules: [{ name: "Score", status: "PASSED", reason: "Score suficiente" }] });
    assert.match(html, /Crédito aprovado/); assert.match(html, /R\$\s?700,00/); assert.match(html, /eval-1/); assert.match(html, /Score suficiente/);
  });
}
