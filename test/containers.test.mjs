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
  assert.match(compose, /SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092/);
  assert.match(dockerfile, /actuator\/health\/readiness|ENTRYPOINT/, "application image must provide its runnable entrypoint");

  for (const variable of ["POSTGRES_DB", "POSTGRES_USER", "POSTGRES_PASSWORD", "KEYCLOAK_ADMIN", "KEYCLOAK_ADMIN_PASSWORD"]) {
    assert.match(envExample, new RegExp(`^${variable}=.+$`, "m"), `${variable} must be documented`);
  }
  assert.doesNotMatch(compose, /(?:PASSWORD|password):\s*(?!\$\{)[^\s]+/, "compose must not embed a password");
});
