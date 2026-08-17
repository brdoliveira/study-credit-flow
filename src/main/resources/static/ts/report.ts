import { API_BASE, escapeHtml, renderError, request } from "./api.ts";

const PAGE_SIZE = 20;
const currency = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const dateTime = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "UTC"
});

export function filterQuery(filters) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== null && value !== undefined && String(value).trim()) query.set(key, String(value));
  }
  return query.toString();
}

function formattedDate(value) {
  const parsed = new Date(value);
  return Number.isNaN(parsed.valueOf()) ? "-" : dateTime.format(parsed).replace(",", "");
}

function emptyResults() {
  return `<div class="empty-state">
    <span><i data-lucide="search-x" aria-hidden="true"></i></span>
    <h2>Nenhuma avaliação encontrada</h2>
    <p>Não há registros correspondentes aos filtros selecionados.</p>
  </div>`;
}

export function renderRows(page) {
  const items = page.items || [];
  if (!items.length) return emptyResults();
  const currentPage = Number(page.page) || 0;
  const size = Number(page.size) || PAGE_SIZE;
  const total = Number(page.total) || 0;
  const totalPages = Math.max(1, Math.ceil(total / size));
  const rows = items.map(item => {
    const approved = item.decision === "APPROVED";
    return `<tr>
      <td class="customer-cell"><strong>${escapeHtml(item.maskedCpf)}</strong></td>
      <td><span class="status-badge status-badge--${approved ? "approved" : "rejected"}">${approved ? "Aprovada" : "Reprovada"}</span></td>
      <td class="money-cell">${currency.format(item.approvedAmount || 0)}</td>
      <td class="date-cell">${formattedDate(item.processedAt)}</td>
      <td><code class="evaluation-id">${escapeHtml(item.evaluationId)}</code></td>
    </tr>`;
  }).join("");
  return `<div class="report-panel">
    <header class="report-panel__summary">
      <div><h2>Resultado da consulta</h2><p>Avaliações ordenadas da mais recente para a mais antiga.</p></div>
      <div class="result-count"><strong>${total}</strong><small>registros</small></div>
    </header>
    <div class="table-responsive">
      <table class="table report-table">
        <thead><tr><th>CPF do cliente</th><th>Decisão</th><th class="text-end">Valor aprovado</th><th>Processada em</th><th>Identificador</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>
    </div>
    <footer class="report-panel__footer">
      <span class="page-status">Página ${currentPage + 1} de ${totalPages}</span>
      <div class="pagination-actions">
        <button type="button" data-report-page="${currentPage - 1}" ${currentPage === 0 ? "disabled" : ""}><i data-lucide="chevron-left" aria-hidden="true"></i>Anterior</button>
        <button type="button" data-report-page="${currentPage + 1}" ${currentPage + 1 >= totalPages ? "disabled" : ""}>Próxima<i data-lucide="chevron-right" aria-hidden="true"></i></button>
      </div>
    </footer>
  </div>`;
}

function setSubmitting(button, submitting) {
  button.disabled = submitting;
  button.innerHTML = submitting
    ? '<span class="button-spinner" aria-hidden="true"></span><span>Consultando...</span>'
    : '<i data-lucide="search" aria-hidden="true"></i><span>Consultar</span>';
  globalThis.lucide?.createIcons();
}

if (typeof document !== "undefined") {
  const form = document.querySelector("#report-filters");
  const feedback = document.querySelector("#feedback");
  const results = document.querySelector("#report-results");
  const submitButton = document.querySelector("#submit-report");
  const filters = () => Object.fromEntries(new FormData(form));
  let currentPage = 0;

  async function list(page = 0) {
    setSubmitting(submitButton, true);
    try {
      const query = filterQuery({ ...filters(), page, size: PAGE_SIZE });
      const response = await request(`?${query}`);
      results.innerHTML = renderRows(await response.json());
      currentPage = page;
      globalThis.lucide?.createIcons();
    } finally {
      setSubmitting(submitButton, false);
    }
  }

  form.addEventListener("submit", async event => {
    event.preventDefault();
    feedback.innerHTML = "";
    try {
      await list(0);
    } catch (error) {
      feedback.innerHTML = renderError(error);
    }
  });

  results.addEventListener("click", async event => {
    const button = event.target.closest("[data-report-page]");
    if (!button || button.disabled) return;
    feedback.innerHTML = "";
    try {
      await list(Number(button.dataset.reportPage));
    } catch (error) {
      feedback.innerHTML = renderError(error);
    }
  });

  const downloadButton = document.querySelector("#download-pdf");
  downloadButton.addEventListener("click", () => {
    window.location.assign(`${API_BASE}/report.pdf?${filterQuery(filters())}`);
  });
  submitButton.disabled = false;
  downloadButton.disabled = false;
}

if (typeof process !== "undefined" && process.env.NODE_TEST_CONTEXT) {
  const { test } = await import("node:test");
  const assert = await import("node:assert/strict");
  test("@spec:AC-042 reutiliza exatamente os filtros na consulta e no PDF", () => {
    const query = filterQuery({ decision: "APPROVED", from: "2026-08-01", to: "2026-08-15" });
    assert.equal(query, "decision=APPROVED&from=2026-08-01&to=2026-08-15");
    assert.equal(`${API_BASE}/report.pdf?${query}`, "/api/v1/credit-evaluations/report.pdf?decision=APPROVED&from=2026-08-01&to=2026-08-15");
  });

  test("relatório apresenta dados formatados e controles de paginação", () => {
    const html = renderRows({ page: 0, size: 20, total: 30, items: [{ customerName: "Ana Silva", maskedCpf: "***.982.247-**", decision: "APPROVED", approvedAmount: 2800, processedAt: "2026-08-17T15:45:00Z", evaluationId: "eval-1" }] });
    assert.match(html, /\*\*\*\.982\.247-\*\*/);
    assert.match(html, /R\$\s?2\.800,00/);
    assert.match(html, /Página 1 de 2/);
    assert.match(html, /Próxima/);
  });
}
