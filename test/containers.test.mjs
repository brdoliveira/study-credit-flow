import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(`../${path}`, import.meta.url), "utf8");

test("@spec:AC-044 docker compose starts the application, PostgreSQL, identity provider and broker with documented variables", () => {
  const compose = read("compose.yaml");
  const envExample = read(".env.example");
  const dockerfile = read("Dockerfile");

  for (const service of ["app", "postgres", "keycloak", "kafka"]) {
    assert.match(compose, new RegExp(`^  ${service}:`, "m"), `missing ${service} service`);
  }
  assert.equal((compose.match(/healthcheck:/g) ?? []).length, 4, "every service needs a health check");
  assert.match(compose, /condition: service_healthy/, "app must wait for healthy dependencies");
  assert.match(compose, /postgres_data:/, "PostgreSQL data must use an explicit volume");
  assert.match(compose, /kafka_data:/, "broker data must use an explicit volume");
  assert.match(compose, /docker\/keycloak\/realm-export\.json/, "Keycloak realm must be imported");
  assert.match(compose, /docker\/keycloak\/themes\/credit-flow/, "Keycloak login theme must be mounted read-only");
  assert.match(compose, /SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092/);
  assert.equal((compose.match(/127\.0\.0\.1:\$\{/g) ?? []).length, 4, "local ports must bind only to loopback");
  assert.match(dockerfile, /actuator\/health\/readiness|ENTRYPOINT/, "application image must provide its runnable entrypoint");

  for (const variable of ["POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "KEYCLOAK_ADMIN", "KEYCLOAK_ADMIN_PASSWORD", "CREDIT_DEMO_PASSWORD"]) {
    assert.match(envExample, new RegExp(`^${variable}=.+$`, "m"), `${variable} must be documented`);
  }
  assert.doesNotMatch(compose, /(?:PASSWORD|password):\s*(?!\$\{)[^\s]+/, "compose must not embed a password");
});

test("Keycloak usa login profissional localizado e sem dependencias externas", () => {
  const realm = read("docker/keycloak/realm-export.json");
  const theme = read("docker/keycloak/themes/credit-flow/login/theme.properties");
  const loginTemplate = read("docker/keycloak/themes/credit-flow/login/login.ftl");
  const css = read("docker/keycloak/themes/credit-flow/login/resources/css/login.css");
  const messages = read("docker/keycloak/themes/credit-flow/login/messages/messages_pt_BR.properties");

  assert.match(realm, /"loginTheme": "credit-flow"/);
  assert.match(realm, /"defaultLocale": "pt-BR"/);
  assert.match(theme, /parent=keycloak\.v2/);
  assert.match(theme, /styles=css\/styles\.css css\/login\.css/);
  assert.match(loginTemplate, /href="\$\{url\.loginRestartFlowUrl\}"/);
  assert.match(loginTemplate, /msg\("backToLogin"\)/);
  assert.match(css, /\.pf-v5-c-login__main/);
  assert.match(css, /\.pf-v5-c-form-control:focus-within/);
  assert.doesNotMatch(css, /https?:\/\//);
  assert.match(messages, /loginAccountTitle=Acesso seguro/);
  assert.match(messages, /backToLogin=Voltar ao início/);
  assert.match(messages, /invalidUserMessage=Usuário ou senha inválidos\./);
});
