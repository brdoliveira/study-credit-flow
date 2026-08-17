import { escapeHtml, renderError, request } from "./api.ts";

const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function evaluationPayload(form) {
  const data = new FormData(form);
  return {
    name: data.get("name"),
    cpf: data.get("cpf"),
    creditScore: Number(data.get("creditScore")),
    currentInvoiceAmount: Number(data.get("currentInvoiceAmount")),
    totalLimit: Number(data.get("totalLimit")),
    availableLimit: Number(data.get("availableLimit")),
    latePayments: Number(data.get("latePayments")),
    monthlySpending: String(data.get("monthlySpending")).split(",").map(value => Number(value.trim()))
  };
}

function rulePresentation(status) {
  if (status === "PASSED") return { label: "Atendida", style: "passed" };
  if (status === "WARNING") return { label: "Atenção", style: "warning" };
  return { label: "Não atendida", style: "failed" };
}

export function renderDecision(result) {
  const approved = result.decision === "APPROVED";
  const rules = (result.rules || []).map(rule => {
    const presentation = rulePresentation(rule.status);
    return `<li class="rule-row">
      <strong>${escapeHtml(rule.name)}</strong>
      <span class="status-badge status-badge--${presentation.style}">${presentation.label}</span>
      <p>${escapeHtml(rule.reason)}</p>
    </li>`;
  }).join("");
  return `<article class="decision-panel decision-${approved ? "approved" : "rejected"}">
    <div class="decision-panel__summary">
      <div class="decision-status">
        <span class="decision-status__icon"><i data-lucide="${approved ? "circle-check-big" : "circle-x"}" aria-hidden="true"></i></span>
        <div><small>Resultado da análise</small><h2>${approved ? "Crédito aprovado" : "Crédito reprovado"}</h2></div>
      </div>
      <div class="decision-metric"><small>Valor aprovado</small><strong>${currency.format(result.approvedAmount || 0)}</strong></div>
      <div class="decision-reference"><small>Identificador da avaliação</small><code>${escapeHtml(result.evaluationId)}</code></div>
    </div>
    <div class="rules-section">
      <h3>Regras avaliadas</h3>
      <ul class="rule-list">${rules}</ul>
    </div>
  </article>`;
}

function setSubmitting(button, submitting) {
  button.disabled = submitting;
  button.innerHTML = submitting
    ? '<span class="button-spinner" aria-hidden="true"></span><span>Processando...</span>'
    : '<i data-lucide="scan-search" aria-hidden="true"></i><span>Avaliar crédito</span>';
  globalThis.lucide?.createIcons();
}

if (typeof document !== "undefined") {
  const form = document.querySelector("#evaluation-form");
  const feedback = document.querySelector("#feedback");
  const decision = document.querySelector("#decision");
  const submitButton = document.querySelector("#submit-evaluation");
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (!form.reportValidity()) return;
    feedback.innerHTML = "";
    decision.innerHTML = "";
    setSubmitting(submitButton, true);
    try {
      const response = await request("", {
        method: "POST",
        headers: { "Content-Type": "application/json", "Idempotency-Key": crypto.randomUUID() },
        body: JSON.stringify(evaluationPayload(form))
      });
      decision.innerHTML = renderDecision(await response.json());
      globalThis.lucide?.createIcons();
      decision.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (error) {
      feedback.innerHTML = renderError(error);
    } finally {
      setSubmitting(submitButton, false);
    }
  });
  submitButton.disabled = false;
}

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test");
  const assert = await import("node:assert/strict");
  test("@spec:AC-041 mostra decisão, valor, regras, motivos e identificador", () => {
    const html = renderDecision({ decision: "APPROVED", approvedAmount: 700, evaluationId: "eval-1", rules: [{ name: "Score", status: "PASSED", reason: "Score suficiente" }] });
    assert.match(html, /Crédito aprovado/);
    assert.match(html, /R\$\s?700,00/);
    assert.match(html, /eval-1/);
    assert.match(html, /Score suficiente/);
  });
}
