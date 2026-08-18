import { correlationId, escapeHtml, renderError, request } from "./api.ts";

const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const fieldPresentation = {
  name: { label: "Nome completo", message: "Informe o nome completo." },
  cpf: { label: "CPF", message: "Informe um CPF válido com 11 dígitos." },
  creditScore: { label: "Score de crédito", message: "Informe um score entre 0 e 1000." },
  currentInvoiceAmount: { label: "Fatura atual", message: "Informe uma fatura igual ou maior que zero." },
  totalLimit: { label: "Limite total", message: "Informe um limite total maior que zero." },
  availableLimit: { label: "Limite disponível", message: "Informe um limite disponível igual ou maior que zero." },
  latePayments: { label: "Pagamentos em atraso", message: "Informe uma quantidade igual ou maior que zero." },
  monthlySpending: { label: "Gastos dos últimos três meses", message: "Informe exatamente três valores iguais ou maiores que zero, separados por vírgula." }
};

export function cpfDigits(value) {
  return String(value ?? "").replace(/\D/g, "").slice(0, 11);
}

export function formatCpf(value) {
  const digits = cpfDigits(value);
  return digits
    .replace(/^(\d{3})(\d)/, "$1.$2")
    .replace(/^(\d{3})\.(\d{3})(\d)/, "$1.$2.$3")
    .replace(/^(\d{3})\.(\d{3})\.(\d{3})(\d)/, "$1.$2.$3-$4");
}

export function isValidCpf(value) {
  const digits = cpfDigits(value);
  if (digits.length !== 11 || digits.split("").every(digit => digit === digits[0])) return false;
  const calculateDigit = length => {
    const sum = digits.slice(0, length).split("").reduce((total, digit, index) => total + Number(digit) * (length + 1 - index), 0);
    const remainder = sum % 11;
    return remainder < 2 ? 0 : 11 - remainder;
  };
  return Number(digits[9]) === calculateDigit(9) && Number(digits[10]) === calculateDigit(10);
}

function spendingItems(value) {
  return String(value ?? "").split(",").map(item => item.trim());
}

function spendingValues(value) {
  return spendingItems(value).map(Number);
}

export function evaluationPayload(form) {
  const data = new FormData(form);
  return {
    name: data.get("name"),
    cpf: cpfDigits(data.get("cpf")),
    creditScore: Number(data.get("creditScore")),
    currentInvoiceAmount: Number(data.get("currentInvoiceAmount")),
    totalLimit: Number(data.get("totalLimit")),
    availableLimit: Number(data.get("availableLimit")),
    latePayments: Number(data.get("latePayments")),
    monthlySpending: spendingValues(data.get("monthlySpending"))
  };
}

function uniqueErrors(errors) {
  const byField = new Map();
  for (const error of errors) if (fieldPresentation[error.field] && !byField.has(error.field)) byField.set(error.field, error);
  const fieldOrder = Object.keys(fieldPresentation);
  return [...byField.values()].sort((left, right) => fieldOrder.indexOf(left.field) - fieldOrder.indexOf(right.field));
}

export function serverValidationErrors(error) {
  if (!Array.isArray(error?.fieldErrors)) return [];
  return uniqueErrors(error.fieldErrors.map(item => {
    const field = String(item?.field ?? "").replace(/Valid$/, "").replace(/\[\d+\].*$/, "");
    return { field, message: fieldPresentation[field]?.message };
  }));
}

function clientValidationErrors(form) {
  const errors = [];
  for (const [field, presentation] of Object.entries(fieldPresentation)) {
    const input = form.elements.namedItem(field);
    if (input?.validity && !input.validity.valid) errors.push({ field, message: presentation.message });
  }

  if (!String(form.elements.namedItem("name")?.value ?? "").trim()) {
    errors.push({ field: "name", message: fieldPresentation.name.message });
  }
  if (!isValidCpf(form.elements.namedItem("cpf")?.value)) {
    errors.push({ field: "cpf", message: fieldPresentation.cpf.message });
  }

  const spending = spendingItems(form.elements.namedItem("monthlySpending")?.value);
  if (spending.length !== 3 || spending.some(value => !value || !Number.isFinite(Number(value)) || Number(value) < 0)) {
    errors.push({ field: "monthlySpending", message: fieldPresentation.monthlySpending.message });
  }
  return uniqueErrors(errors);
}

function clearFieldError(form, field) {
  const input = form.elements.namedItem(field);
  if (!input) return;
  input.removeAttribute("aria-invalid");
  const errorId = `${field}-error`;
  const describedBy = (input.getAttribute("aria-describedby") ?? "").split(/\s+/).filter(id => id && id !== errorId);
  if (describedBy.length) input.setAttribute("aria-describedby", describedBy.join(" "));
  else input.removeAttribute("aria-describedby");
  form.querySelector(`#${errorId}`)?.remove();
}

function clearFieldErrors(form) {
  for (const field of Object.keys(fieldPresentation)) clearFieldError(form, field);
}

function showFieldErrors(form, errors) {
  clearFieldErrors(form);
  for (const { field, message } of errors) {
    const input = form.elements.namedItem(field);
    if (!input) continue;
    const errorId = `${field}-error`;
    input.setAttribute("aria-invalid", "true");
    const describedBy = new Set((input.getAttribute("aria-describedby") ?? "").split(/\s+/).filter(Boolean));
    describedBy.add(errorId);
    input.setAttribute("aria-describedby", [...describedBy].join(" "));
    const fieldError = document.createElement("small");
    fieldError.id = errorId;
    fieldError.className = "field-error";
    fieldError.textContent = message;
    input.closest(".field")?.append(fieldError);
  }
}

function renderValidationSummary(errors, requestCorrelationId = null) {
  const items = errors.map(({ field, message }) => `<li><a href="#${escapeHtml(field)}">${escapeHtml(fieldPresentation[field].label)}: ${escapeHtml(message)}</a></li>`).join("");
  const correlation = requestCorrelationId ? `<small class="correlation-id">Código de acompanhamento: ${escapeHtml(requestCorrelationId)}</small>` : "";
  return `<div class="alert alert-danger" role="alert"><p class="mb-0">Corrija os campos destacados e tente novamente.</p><ul class="validation-summary">${items}</ul>${correlation}</div>`;
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
  const cpfInput = form.elements.namedItem("cpf");

  cpfInput.value = formatCpf(cpfInput.value);
  cpfInput.addEventListener("input", () => { cpfInput.value = formatCpf(cpfInput.value); });
  form.addEventListener("input", event => {
    if (!event.target?.name || !fieldPresentation[event.target.name]) return;
    clearFieldError(form, event.target.name);
    if (feedback.querySelector(".validation-summary")) feedback.innerHTML = "";
  });

  form.addEventListener("submit", async event => {
    event.preventDefault();
    feedback.innerHTML = "";
    decision.innerHTML = "";
    const validationErrors = clientValidationErrors(form);
    if (validationErrors.length) {
      showFieldErrors(form, validationErrors);
      feedback.innerHTML = renderValidationSummary(validationErrors);
      form.elements.namedItem(validationErrors[0].field)?.focus();
      return;
    }
    clearFieldErrors(form);
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
      const validationErrors = serverValidationErrors(error);
      if (validationErrors.length) {
        showFieldErrors(form, validationErrors);
        feedback.innerHTML = renderValidationSummary(validationErrors, correlationId(error));
        form.elements.namedItem(validationErrors[0].field)?.focus();
      } else {
        feedback.innerHTML = renderError(error);
      }
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
  test("formata, valida e normaliza CPF sem alterar o contrato da API", () => {
    assert.equal(formatCpf("52998224725"), "529.982.247-25");
    assert.equal(cpfDigits("529.982.247-25"), "52998224725");
    assert.equal(isValidCpf("529.982.247-25"), true);
    assert.equal(isValidCpf("111.111.111-11"), false);
  });
  test("traduz erros de campos devolvidos pela API", () => {
    const errors = serverValidationErrors({ fieldErrors: [{ field: "cpfValid" }, { field: "monthlySpending[1]" }] });
    assert.deepEqual(errors.map(error => error.field), ["cpf", "monthlySpending"]);
    assert.match(errors[0].message, /CPF válido/);
  });
}
