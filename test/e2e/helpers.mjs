import { spawn } from 'node:child_process';

export const appUrl = process.env.E2E_APP_URL ?? 'http://localhost:8080';
export const keycloakUrl = process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8180';

export class CookieJar {
  #cookies = new Map();
  absorb(response) {
    const values = typeof response.headers.getSetCookie === 'function'
      ? response.headers.getSetCookie()
      : response.headers.get('set-cookie')?.split(/,(?=[^;,]+=[^;,]+)/) ?? [];
    for (const value of values) {
      const [pair] = value.split(';', 1);
      const separator = pair.indexOf('=');
      if (separator > 0) this.#cookies.set(pair.slice(0, separator), pair.slice(separator + 1));
    }
    return response;
  }
  header() { return [...this.#cookies].map(([name, value]) => `${name}=${value}`).join('; '); }
}

export async function browserRequest(jar, url, options = {}) {
  const headers = new Headers(options.headers);
  if (jar.header()) headers.set('cookie', jar.header());
  return jar.absorb(await fetch(url, { ...options, headers, redirect: 'manual' }));
}

async function follow(jar, response, base) {
  const location = response.headers.get('location');
  if (!location) throw new Error(`Expected redirect from ${base}, received ${response.status}`);
  return browserRequest(jar, new URL(location, base));
}

export async function loginAsWriter(password) {
  const jar = new CookieJar();
  let response = await browserRequest(jar, `${appUrl}/`);
  while (response.status >= 300 && response.status < 400) response = await follow(jar, response, response.url || appUrl);
  const action = (await response.text()).match(/<form[^>]+id="kc-form-login"[^>]+action="([^"]+)"/i)?.[1];
  if (!action) throw new Error('Keycloak login form was not rendered');
  response = await browserRequest(jar, new URL(action.replaceAll('&amp;', '&'), keycloakUrl), {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username: 'credit-writer', password, credentialId: '' }),
  });
  while (response.status >= 300 && response.status < 400) response = await follow(jar, response, response.url || appUrl);
  if (!response.ok) throw new Error(`OIDC callback failed: ${response.status}`);
  return jar;
}

export async function waitFor(assertion, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let failure;
  while (Date.now() < deadline) {
    try { return await assertion(); } catch (error) { failure = error; await new Promise(resolve => setTimeout(resolve, 500)); }
  }
  throw failure ?? new Error('Timed out');
}

export function compose(args, timeoutMs = 10_000) {
  const project = process.env.E2E_COMPOSE_PROJECT;
  if (!project) throw new Error('E2E_COMPOSE_PROJECT is required');
  return new Promise((resolve, reject) => {
    const child = spawn('docker', ['compose', '--project-name', project, ...args], {
      stdio: ['ignore', 'pipe', 'pipe'], timeout: timeoutMs,
    });
    let stdout = ''; let stderr = '';
    child.stdout.on('data', chunk => { stdout += chunk; });
    child.stderr.on('data', chunk => { stderr += chunk; });
    child.on('error', reject);
    child.on('close', (code, signal) => code === 0
      ? resolve(stdout.trim())
      : reject(new Error(stderr || `docker compose stopped with code ${code} and signal ${signal}`)));
  });
}

export function validCpf() {
  const digits = [5, 2, 9, 9, 8, 2, 2, 4, 7];
  const check = (items, weight) => { const r = items.reduce((sum, digit, i) => sum + digit * (weight - i), 0) % 11; return r < 2 ? 0 : 11 - r; };
  return [...digits, check(digits, 10), check([...digits, check(digits, 10)], 11)].join('');
}
