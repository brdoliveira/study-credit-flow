import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
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

function filesUnder(fragment) {
  return kotlinFiles.filter((file) => rel(file).includes(fragment));
}

function imports(file) {
  return [...source(file).matchAll(/^\s*import\s+([\w.]+)/gm)].map((match) => match[1]);
}

function dependencyViolations(files, forbidden) {
  return files.flatMap((file) => imports(file)
    .filter((dependency) => forbidden.test(dependency))
    .map((dependency) => `${rel(file)} imports ${dependency}`));
}

test('AC-096: Plataforma compartilhada possui namespace proprio @spec:AC-096', () => {
  const platform = filesUnder('io/github/brdoliveira/creditflow/platform/');
  const legacyInfrastructure = filesUnder('io/github/brdoliveira/creditflow/infrastructure/');
  const evaluationInfrastructure = filesUnder('io/github/brdoliveira/creditflow/evaluation/infrastructure/');
  assert.ok(platform.length > 0, 'o pacote compartilhado platform nao pode estar vazio');
  assert.deepEqual(legacyInfrastructure.map(rel), [], 'o pacote global infrastructure deve ser removido');
  assert.equal(platform.some((file) => file.includes('LoadTestPdfReport')), false,
    'ferramentas de carga não podem entrar no source set produtivo');
  assert.ok(existsSync(resolve('tools/load-test-report/build.gradle.kts')),
    'o relatório de carga deve permanecer em um subprojeto de ferramentas');
  assert.ok(evaluationInfrastructure.length > 0, 'os adaptadores da feature devem permanecer em evaluation.infrastructure');

  const tests = walk(resolve('src/test/kotlin')).filter((file) => file.endsWith('.kt'));
  const legacyDeclarations = [...kotlinFiles, ...tests]
    .filter((file) => /^\s*(?:package|import)\s+io\.github\.brdoliveira\.creditflow\.infrastructure(?:\.|$)/m.test(source(file)))
    .map((file) => file.replaceAll('\\', '/'));
  assert.deepEqual(legacyDeclarations, [], 'declaracoes e imports devem usar creditflow.platform');

  const documentation = `${readFileSync(resolve('docs/architecture.md'), 'utf8')}\n${readFileSync(resolve('docs/adrs/001-modular-monolith.md'), 'utf8')}`;
  assert.match(documentation, /creditflow\/platform|creditflow\.platform|\bplatform\//,
    'a documentacao deve nomear a plataforma compartilhada');
});

function kdocBefore(lines, index) {
  const windowStart = Math.max(0, index - 24);
  const localEnd = lines.slice(windowStart, index).findLastIndex((line) => line.includes('*/'));
  if (localEnd < 0) return null;
  const end = localEnd + windowStart;
  const start = lines.slice(0, end + 1).findLastIndex((line) => line.includes('/**'));
  if (start < 0) return null;
  const between = lines.slice(end + 1, index).join('\n');
  const declarationBetween = between.split('\n').some((line) =>
    /^\s*(?:(?:public|internal|private|protected|data|sealed|enum|fun|open|abstract|override)\s+)*(?:class|interface|object|typealias|fun)\s+\w+/.test(line));
  if (declarationBetween) return null;
  return lines.slice(start, end + 1).join('\n');
}

function documentedDeclarations(file) {
  const lines = source(file).split('\n');
  let depth = 0;
  return lines.flatMap((line, index) => {
    const type = /^\s*(?:(?:public|internal)\s+)?(?:(?:data|sealed|enum|fun)\s+)?(?:class|interface|object|typealias)\b/.test(line);
    const operation = depth <= 1 && /^\s*(?:(?:public|internal|override|open|abstract|suspend|operator|infix|tailrec)\s+)*fun\s+\w+/.test(line);
    const topLevelProperty = depth === 0
      && !/^\s{4,}/.test(line)
      && /^\s*(?:(?:public|internal)\s+)?(?:const\s+)?(?:val|var)\s+\w+/.test(line);
    const result = type || operation || topLevelProperty ? [{ line: index + 1, kdoc: kdocBefore(lines, index) }] : [];
    depth += (line.match(/{/g) ?? []).length - (line.match(/}/g) ?? []).length;
    return result;
  });
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
  const english = /\b(the|this|keeps|returns|when|with|from|for|throws|publishes|records|waits|maps)\b/i;
  const violations = kotlinFiles.flatMap((file) => documentedDeclarations(file)
    .filter(({ kdoc }) => !kdoc || english.test(kdoc))
    .map(({ line }) => `${rel(file)}:${line}`));
  assert.deepEqual(violations, [], 'tipos e operações públicas ou internas precisam de KDoc em português');

  const englishKdocs = kotlinFiles.flatMap((file) => [...source(file).matchAll(/\/\*\*[\s\S]*?\*\//g)]
    .filter((match) => english.test(match[0]))
    .map(() => rel(file)));
  assert.deepEqual(englishKdocs, [], 'não pode existir comentário documental em inglês');
});

test('AC-084: adaptador web separado por responsabilidade @spec:AC-084', () => {
  const web = 'io/github/brdoliveira/creditflow/evaluation/infrastructure/web/';
  for (const directory of ['controller', 'dto', 'mapper', 'error']) {
    assert.ok(filesUnder(`${web}${directory}/`).length > 0, `subpacote web ausente: ${directory}`);
  }
  const controllers = filesUnder(`${web}controller/`);
  const violations = controllers.flatMap((file) => {
    const text = source(file);
    const declaresService = /(?:class|interface|object)\s+(?:Default|\w*Service\b)/.test(text);
    const accessesOutput = /import\s+.*(?:Repository|ObjectMapper|Serializer|PdfCreditEvaluationReportGenerator)/.test(text);
    return declaresService || accessesOutput ? [rel(file)] : [];
  });
  assert.deepEqual(violations, [], 'controllers não podem conter serviços nem acessar adaptadores de saída');
});

test('AC-085: aplicação depende diretamente do domínio @spec:AC-085', () => {
  const core = filesUnder('io/github/brdoliveira/creditflow/evaluation/application/EvaluateRevolvingCreditUseCase.kt');
  assert.equal(core.length, 1, 'caso de uso principal não encontrado');
  const text = source(core[0]);
  for (const dependency of ['domain.rule.RuleEngine', 'domain.calculation.CreditLimitCalculator', 'domain.CreditEvaluation']) {
    assert.match(text, new RegExp(`import\\s+io\\.github\\.brdoliveira\\.creditflow\\.evaluation\\.${dependency.replaceAll('.', '\\.')}`));
  }
  assert.doesNotMatch(text, /application\.port\.(?:RuleEngine|CreditLimitCalculator)/, 'regra e cálculo não devem ser portas internas');
  const legacyImports = filesUnder('io/github/brdoliveira/creditflow/evaluation/application/')
    .filter((file) => /creditflow\.(?:application\.evaluation|domain\.)/.test(source(file)))
    .map(rel);
  assert.deepEqual(legacyImports, [], 'a aplicação não deve importar os pacotes legados');
});

test('AC-090: Fronteiras de dependência são verificadas integralmente @spec:AC-090', () => {
  const evaluation = 'io/github/brdoliveira/creditflow/evaluation/';
  const domain = filesUnder(`${evaluation}domain/`);
  const application = filesUnder(`${evaluation}application/`);
  assert.ok(domain.length > 0, 'o domínio real de evaluation não pode ser um diretório vazio');
  assert.ok(application.length > 0, 'a aplicação de evaluation não pode ser um diretório vazio');

  const externalToDomain = /^io\.github\.brdoliveira\.creditflow\.(?:(?:evaluation\.)?(?:application|infrastructure)|platform)(?:\.|$)/;
  const infrastructureToApplication = /^io\.github\.brdoliveira\.creditflow\.(?:evaluation\.infrastructure|platform)(?:\.|$)/;
  assert.deepEqual(
    dependencyViolations(domain, externalToDomain),
    [],
    'o domínio não pode depender das camadas de aplicação ou infraestrutura',
  );
  assert.deepEqual(
    dependencyViolations(application, infrastructureToApplication),
    [],
    'a aplicação não pode depender de adaptadores ou infraestrutura compartilhada',
  );
});

test('AC-086: modelo de avaliação sem duplicações conceituais @spec:AC-086', () => {
  const domain = filesUnder('io/github/brdoliveira/creditflow/evaluation/domain/');
  const names = domain.flatMap(declarations);
  for (const required of ['CreditEvaluation', 'CreditDecision', 'RuleResult']) {
    assert.ok(names.includes(required), `modelo de domínio ausente: ${required}`);
  }
  const forbidden = /(?:Decision|Severity|RuleStatus|RuleState|EvaluationSnapshot|EvaluationState|EvaluationModel)/;
  const duplicates = kotlinFiles
    .filter((file) => !rel(file).includes('/evaluation/domain/'))
    .filter((file) => declarations(file).some((name) => forbidden.test(name)));
  assert.deepEqual(duplicates, [], 'conceitos de avaliação devem ter uma única representação no domínio');
  assert.equal(domain.some((file) => /jackson|jakarta\.persistence|org\.springframework/.test(source(file))), false, 'serialização deve ficar nos adaptadores');
  const evaluationContext = domain.find((file) => file.endsWith('CreditEvaluationContext.kt'));
  assert.doesNotMatch(source(evaluationContext), /customerName|\bcpf\b/i,
    'o contexto das regras não deve transportar identificação pessoal sem uso de negócio');
});

test('AC-087: casos de uso fora do adaptador web @spec:AC-087', () => {
  const controllers = filesUnder('io/github/brdoliveira/creditflow/evaluation/infrastructure/web/controller/');
  for (const name of ['CreditEvaluationController.kt', 'CreditEvaluationReportController.kt']) {
    assert.ok(controllers.some((file) => file.endsWith(name)), `controller ausente: ${name}`);
  }
  const violations = controllers.flatMap((file) => {
    const text = source(file);
    const hasUseCase = /import\s+io\.github\.brdoliveira\.creditflow\.evaluation\.application\.[^\n]*UseCase/.test(text);
    const accessesInfrastructure = /(?:Repository|ObjectMapper|Serializer|Service|PdfCreditEvaluationReportGenerator)/.test(text);
    return !hasUseCase || accessesInfrastructure ? [rel(file)] : [];
  });
  assert.deepEqual(violations, [], 'controllers devem delegar a casos de uso e não acessar infraestrutura');
});

test('AC-088: contratos funcionais preservados @spec:AC-088', () => {
  const tests = walk(resolve('src/test/kotlin')).filter((file) => file.endsWith('.kt'));
  const requiredAreas = ['web', 'security', 'persistence', 'messaging', 'report', 'observability'];
  for (const area of requiredAreas) {
    assert.ok(tests.some((file) => file.replaceAll('\\', '/').includes(`/${area}/`)), `suíte ausente: ${area}`);
  }
  assert.equal(tests.some((file) => /@Disabled\b/.test(source(file))), false, 'testes não podem ser desabilitados para concluir a refatoração');
});

test('AC-089: arquitetura documentada corresponde ao código @spec:AC-089', () => {
  const architecture = resolve('docs/architecture.md');
  const adr = resolve('docs/adrs/001-modular-monolith.md');
  assert.ok(existsSync(architecture), 'documento de arquitetura ausente');
  assert.ok(existsSync(adr), 'ADR do monólito modular ausente');
  const documentation = `${readFileSync(architecture, 'utf8')}\n${readFileSync(adr, 'utf8')}`;
  for (const term of ['evaluation/domain', 'evaluation/application', 'web/controller', 'web/dto', 'PostgreSQL', 'PDF']) {
    assert.ok(documentation.includes(term), `documentação não descreve ${term}`);
  }
});

test('AC-095: Documentação e contratos continuam coerentes @spec:AC-095', () => {
  const architecture = readFileSync(resolve('docs/architecture.md'), 'utf8');
  const adr = readFileSync(resolve('docs/adrs/001-modular-monolith.md'), 'utf8');
  const documentation = `${architecture}\n${adr}`;
  const requiredPackages = [
    'evaluation/domain',
    'evaluation/application',
    'evaluation/application/event',
    'evaluation/infrastructure',
    'evaluation/infrastructure/outbox',
    'evaluation/infrastructure/messaging',
  ];
  for (const packagePath of requiredPackages) {
    assert.ok(filesUnder(`io/github/brdoliveira/creditflow/${packagePath}/`).length > 0,
      `pacote produtivo ausente: ${packagePath}`);
    assert.ok(documentation.includes(packagePath), `documentação não descreve ${packagePath}`);
  }

  const eventContract = filesUnder('io/github/brdoliveira/creditflow/evaluation/application/event/CreditEvaluationCompleted.kt');
  assert.equal(eventContract.length, 1, 'contrato Kotlin do evento de avaliação ausente');
  assert.match(documentation, /CreditEvaluationCompleted/, 'a documentação deve registrar a fonte do contrato da outbox');

  const tests = walk(resolve('src/test/kotlin')).filter((file) => file.endsWith('.kt'));
  for (const area of ['web', 'security', 'persistence', 'messaging', 'report', 'observability']) {
    assert.ok(tests.some((file) => file.replaceAll('\\', '/').includes(`/${area}/`)),
      `contrato funcional sem cobertura: ${area}`);
  }
  assert.equal(tests.some((file) => /@(?:Disabled|Ignore)\b/.test(source(file))), false,
    'nenhum contrato funcional pode ser desabilitado durante a refatoração');
});

test('AC-100: Logs JSON estruturados têm identidade e correlação documentadas @spec:AC-100', () => {
  const logback = readFileSync(resolve('src/main/resources/logback-spring.xml'), 'utf8');
  const observability = readFileSync(resolve('src/main/resources/application-observability.yml'), 'utf8');
  const readme = readFileSync(resolve('README.md'), 'utf8');
  const architecture = readFileSync(resolve('docs/architecture.md'), 'utf8');
  const environment = readFileSync(resolve('.env.example'), 'utf8');

  assert.match(logback, /StructuredLogEncoder/, 'o console deve usar o encoder estruturado do Spring Boot');
  assert.match(logback, /<format>\$\{CONSOLE_LOG_STRUCTURED_FORMAT\}<\/format>/,
    'o encoder deve respeitar o formato estruturado configurado');
  assert.match(observability, /console:\s*logstash/, 'o console deve produzir Logstash JSON');
  for (const field of ['service.name', 'service.version', 'service.environment']) {
    assert.match(observability, new RegExp(field.replace('.', '\\.')), `configuração JSON sem ${field}`);
  }
  for (const variable of ['APP_VERSION=local', 'APP_ENVIRONMENT=local']) {
    assert.ok(environment.includes(variable), `.env.example deve expor ${variable}`);
  }
  for (const field of ['@timestamp', 'level', 'logger_name', 'message', 'correlationId', 'traceId', 'spanId']) {
    assert.ok(readme.includes(field) && architecture.includes(field),
      `a operação deve documentar o campo JSON ${field}`);
  }
  assert.match(readme, /docker compose logs app[\s\S]*correlationId/,
    'README deve explicar como buscar logs pelo correlationId');
});

test('AC-101: Documentação e configuração protegem dados sensíveis e volume nominal @spec:AC-101', () => {
  const observability = readFileSync(resolve('src/main/resources/application-observability.yml'), 'utf8');
  const documentation = `${readFileSync(resolve('README.md'), 'utf8')}\n${readFileSync(resolve('docs/architecture.md'), 'utf8')}`;

  for (const field of ['cpf', 'token', 'amount', 'requestBody', 'payload']) {
    assert.match(observability, new RegExp(`-\\s+${field}\\b`),
      `a configuração JSON deve excluir ${field}`);
  }
  for (const term of ['CPF', 'token', 'valores financeiros', 'corpo de requisição', 'payload de evento']) {
    assert.match(documentation, new RegExp(`nunca registre[\\s\\S]{0,180}${term}`, 'i'),
      `a documentação deve proibir o registro de ${term}`);
  }
  for (const level of ['DEBUG', 'INFO', 'WARN', 'ERROR']) {
    assert.ok(documentation.includes(level), `a documentação deve definir o nível ${level}`);
  }
  assert.match(documentation, /não geram um `INFO` por item/i,
    'a documentação deve vedar INFO por sucesso nominal individual');
});
