#!/usr/bin/env node
// Motor ONP versionado no repositório para que CI não dependa de skill local ou npx.
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { spawnSync } from 'node:child_process';

const ONP_SPEC_VERSION = '1.0.0';
const [command, ...args] = process.argv.slice(2);

function fail(message) {
  console.error(`onp-spec ${ONP_SPEC_VERSION}: ${message}`);
  process.exitCode = 1;
}

function run(commandLine, commandArgs) {
  const result = spawnSync(commandLine, commandArgs, { stdio: 'inherit', shell: process.platform === 'win32' });
  if (result.status !== 0) process.exitCode = result.status ?? 1;
}

function audit() {
  const required = ['.spec/constituicao.md', '.spec/features'];
  const missing = required.filter((file) => !existsSync(file));
  if (missing.length > 0) return fail(`arquivos de especificação ausentes: ${missing.join(', ')}`);

  const walk = (directory) => readdirSync(directory).flatMap((entry) => {
    const target = `${directory}/${entry}`;
    return statSync(target).isDirectory() ? walk(target) : [target];
  });
  const featureSpecs = walk('.spec/features').filter((file) => file.endsWith('/spec.md'));
  const criteria = new Set(featureSpecs.flatMap((file) => [...readFileSync(file, 'utf8').matchAll(/\bAC-\d{3}\b/g)].map(([id]) => id)));
  const testRoots = ['test', 'src/test', 'src/main/resources/static/ts'].filter(existsSync);
  const testText = testRoots.flatMap(walk)
    .filter((file) => /\.(?:kt|js|mjs|ts)$/.test(file))
    .map((file) => readFileSync(file, 'utf8'))
    .join('\n');
  const missingCriteria = [...criteria].filter((criterion) => !testText.includes(`@spec:${criterion}`));
  if (missingCriteria.length > 0) return fail(`critérios sem teste rastreável: ${missingCriteria.join(', ')}`);
  if (/\b(?:skip|todo)\s*\(/.test(testText)) return fail('testes rastreáveis não podem ser skip/todo');
  console.log(`onp-spec ${ONP_SPEC_VERSION}: audit ${args.includes('--ci') ? 'CI ' : ''}aprovado (${criteria.size} critérios)`);
}

if (command === 'verify') {
  run(process.platform === 'win32' ? 'gradlew.bat' : './gradlew', ['test', '--no-daemon']);
  if (process.exitCode) process.exit(process.exitCode);
  run('node', ['--test', '--test-reporter=tap', 'test/*.test.mjs', 'src/test/resources/performance/*.test.js', 'src/main/resources/static/ts/*.ts']);
  if (process.exitCode) process.exit(process.exitCode);
  run('node', ['scripts/junit-to-tap.mjs']);
} else if (command === 'audit') {
  audit();
} else {
  fail('use verify <feature> ou audit --ci');
}
