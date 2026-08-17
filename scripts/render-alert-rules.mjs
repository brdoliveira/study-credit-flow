import { readFile, writeFile } from 'node:fs/promises';

const thresholdsPath = new URL('../observability/prometheus/alert-thresholds.json', import.meta.url);
const templatePath = new URL('../observability/prometheus/alerts.template.yml', import.meta.url);
const outputPath = new URL('../observability/prometheus/alerts.yml', import.meta.url);
const check = process.argv.includes('--check');

const thresholds = JSON.parse(await readFile(thresholdsPath, 'utf8'));
let rendered = await readFile(templatePath, 'utf8');
for (const [name, value] of Object.entries(thresholds)) {
  rendered = rendered.replaceAll(`{{${name}}}`, String(value));
}
const unresolved = [...rendered.matchAll(/\{\{([^}]+)}}/g)].map(match => match[1]);
if (unresolved.length) throw new Error(`Unresolved alert thresholds: ${unresolved.join(', ')}`);

if (check) {
  const current = await readFile(outputPath, 'utf8');
  if (current !== rendered) throw new Error('alerts.yml is stale; run node scripts/render-alert-rules.mjs');
} else {
  await writeFile(outputPath, rendered);
}
