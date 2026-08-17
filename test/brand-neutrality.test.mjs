import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const forbiddenBrand = String.fromCharCode(105, 116, 97, 117);
const expectedPackage = 'io.github.brdoliveira.creditflow';
const expectedPath = expectedPackage.replaceAll('.', '/');

function comparable(value) {
  return value.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

function publicFiles() {
  return execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {
    cwd: root,
    encoding: 'utf8',
  }).split('\0').filter(path => path && existsSync(new URL(path, root)));
}

test('@spec:AC-080 árvore publicável não contém referência à marca original', () => {
  const violations = [];
  for (const path of publicFiles()) {
    if (comparable(path).includes(forbiddenBrand)) violations.push(`${path} (nome)`);
    const content = readFileSync(new URL(path, root)).toString('utf8');
    if (comparable(content).includes(forbiddenBrand)) violations.push(`${path} (conteúdo)`);
  }
  assert.deepEqual(violations, []);
});

test('@spec:AC-081 fontes Kotlin usam namespace autoral alinhado ao grupo Gradle', () => {
  const kotlinFiles = publicFiles().filter(path => /^src\/(main|test)\/kotlin\/.*\.kt$/.test(path));
  const qualifiedUsages = [];
  assert.ok(kotlinFiles.length > 0);
  for (const path of kotlinFiles) {
    assert.ok(path.includes(`/kotlin/${expectedPath}/`), `Caminho fora do namespace: ${path}`);
    const content = readFileSync(new URL(path, root), 'utf8');
    assert.match(content, new RegExp(`^package ${expectedPackage.replaceAll('.', '\\.')}`, 'm'));
    content.split(/\r?\n/).forEach((line, index) => {
      const trimmed = line.trimStart();
      if (line.includes(expectedPackage) && !trimmed.startsWith('package ') && !trimmed.startsWith('import ')) {
        qualifiedUsages.push(`${path}:${index + 1}`);
      }
    });
  }
  assert.deepEqual(qualifiedUsages, [], 'Use imports ou aliases em vez do caminho totalmente qualificado');
  assert.match(readFileSync(new URL('build.gradle.kts', root), 'utf8'), /group = "io\.github\.brdoliveira"/);
});
