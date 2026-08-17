import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("frontend serves dependencies locally and does not require inline scripts", () => {
  for (const page of ["src/main/resources/static/index.html", "src/main/resources/static/report.html"]) {
    const html = read(page);
    assert.doesNotMatch(html, /https?:\/\//, `${page} must not load runtime dependencies from a CDN`);
    assert.match(html, /\/webjars\/bootstrap\/5\.3\.8\/css\/bootstrap\.min\.css/);
    assert.match(html, /\/webjars\/lucide\/1\.16\.0\/dist\/umd\/lucide\.min\.js/);
    assert.doesNotMatch(html, /<script(?![^>]*\bsrc=)[^>]*>/, `${page} must not contain inline scripts`);
  }

  const security = read("src/main/kotlin/io/github/brdoliveira/creditflow/platform/security/SecurityConfiguration.kt");
  assert.match(security, /script-src 'self'/);
  assert.doesNotMatch(security, /unsafe-inline|unsafe-eval/);
});
