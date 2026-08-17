import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { test } from 'node:test';

const root = resolve('src/main/kotlin');
const kotlinFiles = walk(root).filter((file) => file.endsWith('.kt'));
const source = (file) => readFileSync(file, 'utf8');
const rel = (file) => relative(root, file).replaceAll('\\', '/');

function walk(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? walk(path) : [path];
  });
}

function declarations(file) {
  const text = source(file).replace(/\/\/.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '');
  let depth = 0;
  const result = [];
  for (const line of text.split('\n')) {
    const match = line.match(/^\s*(?:(public|internal|private|protected)\s+)?(?:(?:data|sealed|enum|fun)\s+)?(class|interface|object|typealias)\s+(\w+)/);
    if (depth === 0 && match && match[1] !== 'private') result.push(match[3]);
    depth += (line.match(/{/g) ?? []).length - (line.match(/}/g) ?? []).length;
  }
  return result;
}

function packageOf(text) {
  return text.match(/^package\s+([^\n]+)/m)?.[1]?.trim();
}

function filesUnder(fragment) {
  return kotlinFiles.filter((file) => rel(file).includes(fragment));
}

test('AC-082: um tipo público principal por arquivo @spec:AC-082', () => {
  const violations = kotlinFiles.flatMap((file) => {
    const names = declarations(file);
    const expected = file.split(/[\\/]/).at(-1).replace(/\.kt$/, '');
    return names.length !== 1 || names[0] !== expected
      ? [`${rel(file)}: ${names.join(', ') || 'nenhum'} (esperado apenas ${expected})`]
      : [];
  });
  assert.deepEqual(violations, [], 'cada arquivo Kotlin deve conter exatamente seu tipo principal');
});

test('AC-083: KDocs consistentes em português @spec:AC-083', () => {
  const violations = [];
  for (const file of kotlinFiles) {
    const lines = source(file).split('\n');
    lines.forEach((line, index) => {
      if (!/^\s*(?:(?:public|internal)\s+)?(?:(?:data|sealed|enum|fun)\s+)?(?:class|interface|object|typealias|fun|val|var)\b/.test(line)) return;
      const before = lines.slice(Math.max(0, index - 8), index).join('\n');
      const kdoc = before.match(/\/\*\*[\s\S]*?\*\/\s*$/)?.[0];
      if (!kdoc || !/(?:[áàâãéêíóôõúç]|\b(?:o|a|os|as|de|da|do|para|com|que|em|por|uma|um)\b)/i.test(kdoc) || /\b(the|this|keeps|returns|when|with|from|for)\b/i.test(kdoc)) {
        violations.push(`${rel(file)}:${index + 1}`);
      }
    });
  }
  assert.deepEqual(violations, [], 'tipos e membros públicos/internos precisam de KDoc em português');
});

test('AC-084: adaptador web separado por responsabilidade @spec:AC-084', () => {
  const web = 'io/github/brdoliveira/creditflow/evaluation/infrastructure/web/';
  const expected = ['controller', 'dto', 'mapper', 'error'];
  for (const directory of expected) assert.ok(filesUnder(`${web}${directory}/`).length > 0, `subpacote web ausente: ${directory}`);
  const controllerFiles = filesUnder(`${web}controller/`);
  assert.ok(controllerFiles.length > 0, 'controllers não encontrados no subpacote próprio');
  const violations = controllerFiles.flatMap((file) => {
    const text = source(file);
    return /(?:class|interface|object)\s+(?:Default|\w*Service\b)/.test(text) || /application\.port\./.test(text)
      ? [rel(file)] : [];
  });
  assert.deepEqual(violations, [], 'controllers não podem conter serviços nem depender de portas');
});

test('AC-085: aplicação depende diretamente do domínio @spec:AC-085', () => {
  const application = filesUnder('io/github/brdoliveira/creditflow/evaluation/application/');
  assert.ok(application.length > 0, 'pacote application da feature não encontrado');
  const violations = application.flatMap((file) => {
    const text = source(file);
    const isUseCase = /UseCase\.kt$/.test(file);
    const importsDomain = /import\s+io\.github\.brdoliveira\.creditflow\.evaluation\.domain\./.test(text);
    const importsForbiddenPort = /import\s+io\.github\.brdoliveira\.creditflow\.evaluation\.application\.port\.(?!CreditEvaluationRepository|IdempotencyRepository)/.test(text);
    const importsLegacy = /application\.evaluation\./.test(text);
    return (isUseCase && !importsDomain) || importsForbiddenPort || importsLegacy ? [rel(file)] : [];
  });
  assert.deepEqual(violations, [], 'casos de uso devem usar o domínio e somente portas de recursos externos');
});

test('AC-086: modelo de avaliação sem duplicações conceituais @spec:AC-086', () => {
  const domain = filesUnder('io/github/brdoliveira/creditflow/evaluation/domain/');
  const names = domain.flatMap(declarations);
  for (const required of ['CreditEvaluation', 'CreditDecision', 'RuleResult']) assert.ok(names.includes(required), `modelo de domínio ausente: ${required}`);
  const forbidden = /(?:Decision|Severity|RuleStatus|RuleState|EvaluationSnapshot|EvaluationState|EvaluationModel)/;
  const duplicates = kotlinFiles
    .filter((file) => !rel(file).includes('/evaluation/domain/'))
    .filter((file) => declarations(file).some((name) => forbidden.test(name)));
  assert.deepEqual(duplicates, [], 'conceitos de avaliação devem ter uma única representação no domínio');
  assert.equal(domain.some((file) => /jackson|jakarta\.persistence|org\.springframework/.test(source(file))), false, 'serialização deve ficar nos adaptadores');
});

test('AC-087: casos de uso fora do adaptador web @spec:AC-087', () => {
  const controllers = filesUnder('io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/');
  for (const name of ['CreditEvaluationController.kt', 'CreditEvaluationReportController.kt']) assert.ok(controllers.some((file) => file.endsWith(name)), `controller ausente: ${name}`);
  const violations = controllers.flatMap((file) => {
    const text = source(file);
    const hasUseCase = /import\s+io\.github\.brdoliveira\.creditflow\.evaluation\.application\.(?:[^\n]*UseCase)/.test(text);
    const accessesInfrastructure = /(?:Repository|ObjectMapper|Serializer|Service|PdfCreditEvaluationReportGenerator)/.test(text);
    return !hasUseCase || accessesInfrastructure ? [rel(file)] : [];
  });
  assert.deepEqual(violations, [], 'controllers devem delegar a casos de uso e não acessar infraestrutura');
});
