import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const workflowPath = new URL('../.github/workflows/ci.yml', import.meta.url);
const workflow = await readFile(workflowPath, 'utf8');

test('@spec:AC-045 pipeline blocks an invalid change before the image build', () => {
  assert.match(workflow, /quality-gates:/);
  assert.match(workflow, /container-image:\s*[\s\S]*?needs: quality-gates/);

  const gateCommands = [
    './gradlew --no-daemon clean compileKotlin',
    './gradlew --no-daemon test',
    './gradlew --no-daemon detekt --config config/detekt/detekt.yml',
    'node --test',
    'npx --yes onp-spec audit --ci',
  ];
  const imageBuild = 'docker build --tag credito-rotativo:${{ github.sha }} .';

  let previousPosition = -1;
  for (const command of gateCommands) {
    const position = workflow.indexOf(command);
    assert.ok(position > previousPosition, `missing or misplaced CI gate: ${command}`);
    previousPosition = position;
  }

  assert.ok(workflow.indexOf(imageBuild) > previousPosition);
  assert.doesNotMatch(workflow, /continue-on-error:\s*true/);
});
