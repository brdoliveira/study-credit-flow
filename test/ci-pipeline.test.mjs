import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const root = new URL('../', import.meta.url);
const read = (path) => readFile(new URL(path, root), 'utf8');

test('@spec:AC-045 @spec:AC-071 CI bloqueia mudança inválida e audita com o motor versionado', async () => {
  const [workflow, tool, manifest, lock] = await Promise.all([
    read('.github/workflows/ci.yml'),
    read('tools/onp-spec/onp-spec.mjs'),
    read('package.json'),
    read('package-lock.json'),
  ]);

  assert.match(workflow, /node tools\/onp-spec\/onp-spec\.mjs verify prontidao-entrega/);
  assert.match(workflow, /node tools\/onp-spec\/onp-spec\.mjs audit --ci/);
  assert.doesNotMatch(workflow, /\bnpx\b[\s\S]*?\bonp-spec\b/);
  assert.match(tool, /ONP_SPEC_VERSION = '1\.0\.0'/);
  assert.match(manifest, /"packageManager": "npm@10\.9\.2"/);
  assert.match(lock, /"lockfileVersion": 3/);
});

test('@spec:AC-072 integração real com PostgreSQL, Kafka e jornada HTTP bloqueia a imagem', async () => {
  const workflow = await read('.github/workflows/ci.yml');
  const integrationStart = 'docker compose up --detach --wait postgres kafka';
  const integrationTest = './gradlew --no-daemon test --tests "*IT"';
  const imageBuild = 'docker build --tag credito-rotativo:${{ github.sha }} .';

  assert.match(workflow, /system-gates:\s*[\s\S]*?needs: integration-gates/);
  assert.match(workflow, /container-image:\s*[\s\S]*?needs: system-gates/);
  assert.ok(workflow.indexOf(integrationStart) >= 0, 'CI must start real PostgreSQL and Kafka');
  assert.ok(workflow.indexOf(integrationTest) > workflow.indexOf(integrationStart));
  assert.ok(workflow.indexOf(imageBuild) > workflow.indexOf(integrationTest));
  assert.match(workflow, /SPRING_KAFKA_BOOTSTRAP_SERVERS: localhost:9092/);
});

test('@spec:AC-073 supply chain bloqueia segredos e vulnerabilidades e publica SBOM versionada', async () => {
  const [workflow, trivy, gitleaks] = await Promise.all([
    read('.github/workflows/ci.yml'),
    read('config/security/trivy.yaml'),
    read('config/security/gitleaks.toml'),
  ]);

  assert.match(workflow, /gitleaks:v8\.30\.1/);
  assert.match(workflow, /gitleaks:v8\.30\.1 git/);
  assert.match(workflow, /trivy:0\.74\.0/);
  assert.match(workflow, /syft:v1\.18\.0/);
  assert.match(workflow, /--exit-code 1/);
  assert.match(workflow, /sbom-\$\{\{ github\.sha \}\}\.cdx\.json/);
  assert.match(workflow, /actions\/upload-artifact@v4/);
  assert.doesNotMatch(workflow, /continue-on-error:\s*true/);
  assert.match(trivy, /severity:\s*HIGH,CRITICAL/);
  assert.match(gitleaks, /useDefault\s*=\s*true/);
});

test('CI bloqueia regressao de cobertura e publica o relatorio JaCoCo', async () => {
  const [workflow, build] = await Promise.all([
    read('.github/workflows/ci.yml'),
    read('build.gradle.kts'),
  ]);

  assert.match(workflow, /jacocoTestCoverageVerification/);
  assert.match(workflow, /kotlin-coverage-\$\{\{ github\.sha \}\}/);
  assert.match(workflow, /build\/reports\/jacoco\/test/);
  assert.match(build, /counter = "LINE"[\s\S]*minimum = "0\.85"/);
  assert.match(build, /counter = "BRANCH"[\s\S]*minimum = "0\.55"/);
  assert.match(build, /tasks\.check[\s\S]*jacocoTestCoverageVerification/);
});

test('CI executa jornada de browser, WCAG e recuperacao antes da imagem', async () => {
  const workflow = await read('.github/workflows/ci.yml');
  assert.match(workflow, /system-gates:[\s\S]*needs: integration-gates/);
  assert.match(workflow, /npm exec playwright install --with-deps chromium/);
  assert.match(workflow, /\.\/scripts\/system-tests\.sh/);
  assert.match(workflow, /browser-diagnostics-\$\{\{ github\.sha \}\}/);
  assert.match(workflow, /container-image:[\s\S]*needs: system-gates/);
});
