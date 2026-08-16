import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';

const root = path.resolve('build/test-results/test');

async function xmlFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const target = path.join(directory, entry.name);
    if (entry.isDirectory()) return xmlFiles(target);
    return entry.isFile() && entry.name.endsWith('.xml') ? [target] : [];
  }));
  return nested.flat();
}

function decode(value) {
  return value
    .replaceAll('&quot;', '"')
    .replaceAll('&apos;', "'")
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&amp;', '&');
}

const tests = [];
for (const file of await xmlFiles(root)) {
  const xml = await readFile(file, 'utf8');
  const regex = /<testcase\b([^>]*)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  for (const match of xml.matchAll(regex)) {
    const name = /\bname="([^"]*)"/.exec(match[1])?.[1];
    if (!name || !name.includes('@spec:') && !name.includes('@principle:')) continue;
    const body = match[2] ?? '';
    const failed = /<(failure|error)\b/.test(body);
    const skipped = /<skipped\b/.test(body);
    tests.push({ name: decode(name), failed, skipped });
  }
}

console.log('TAP version 13');
tests.forEach((test, index) => {
  const directive = test.skipped ? ' # SKIP' : '';
  console.log(`${test.failed ? 'not ok' : 'ok'} ${index + 1} - ${test.name}${directive}`);
});
console.log(`1..${tests.length}`);

if (tests.length === 0 || tests.some((test) => test.failed || test.skipped)) process.exitCode = 1;
