import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { validCpf } from '../e2e/helpers.mjs';

const password = process.env.CREDIT_DEMO_PASSWORD;

async function login(page) {
  expect(password, 'CREDIT_DEMO_PASSWORD must be loaded from .env or CI secrets').toBeTruthy();
  await page.goto('/');
  await page.locator('#username').fill('credit-writer');
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: 'Avaliação de crédito' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Avaliar crédito' })).toBeEnabled();
}

async function expectNoAutomaticAccessibilityViolations(page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(results.violations).toEqual([]);
}

test('operador cria uma avaliação, consulta o histórico e baixa o PDF', async ({ page }) => {
  await login(page);
  await page.getByLabel('Nome completo').fill('Cliente E2E');
  await page.getByLabel('CPF').fill(validCpf());
  await page.getByLabel('Score de crédito').fill('800');
  await page.getByLabel('Fatura atual').fill('100');
  await page.getByLabel('Limite total').fill('2000');
  await page.getByLabel('Limite disponível').fill('1900');
  await page.getByLabel('Pagamentos em atraso').fill('0');
  await page.getByLabel('Gastos dos últimos três meses').fill('100, 110, 120');
  await page.getByRole('button', { name: 'Avaliar crédito' }).click();

  await expect(page.getByRole('heading', { name: 'Crédito aprovado' })).toBeVisible();
  const evaluationId = await page.locator('.decision-reference code').textContent();
  expect(evaluationId).toBeTruthy();

  await page.getByRole('link', { name: 'Relatórios' }).click();
  await expect(page.getByRole('heading', { name: 'Relatório de avaliações' })).toBeVisible();
  await page.getByRole('button', { name: 'Consultar' }).click();
  await expect(page.locator('.evaluation-id', { hasText: evaluationId })).toBeVisible();

  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/\.pdf$/);
});

test('telas autenticadas não têm violações automáticas WCAG A ou AA', async ({ page }) => {
  await login(page);
  await expectNoAutomaticAccessibilityViolations(page);

  await page.getByRole('link', { name: 'Relatórios' }).click();
  await expect(page.getByRole('heading', { name: 'Relatório de avaliações' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Consultar' })).toBeEnabled();
  await expectNoAutomaticAccessibilityViolations(page);
});
