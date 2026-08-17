# Crédito rotativo — fluxo de avaliação

Aplicação demonstrativa para avaliar concessão de crédito rotativo de forma explicável. O backend Kotlin/Spring Boot executa todas as regras, calcula o valor elegível, persiste uma fotografia imutável da decisão, publica evento via outbox e expõe consulta, PDF e uma interface web simples.

## Visão rápida

- Java 21, Kotlin e Spring Boot;
- PostgreSQL local e Aurora PostgreSQL como evolução proposta;
- OAuth 2.0/OIDC com Keycloak local e escopos separados;
- mensageria compatível com Kafka e transactional outbox;
- PDF gerado no backend com PDFBox;
- métricas Prometheus, correlação, liveness e readiness;
- rastreabilidade mecânica entre spec, tarefas e testes em `.spec/`.

A arquitetura e o caminho para AWS estão em [docs/architecture.md](docs/architecture.md). As decisões e seus trade-offs estão em [docs/adrs](docs/adrs).

## Logs operacionais

O console emite uma linha JSON Logstash por registro. Para investigar uma requisição ou evento, copie o `correlationId` devolvido pela API e filtre os logs por esse campo; por exemplo, no Compose:

```powershell
docker compose logs app | Select-String '"correlationId":"<correlationId>"'
```

Em CloudWatch ou outro agregador JSON, pesquise o campo `correlationId` pelo valor exato. Os registros incluem `timestamp`, `level`, `logger`, `message`, `service.name`, `service.version` e `service.environment`; quando o rastreamento estiver disponível, também incluem `correlationId`, `traceId` e `spanId`.

Use `DEBUG` para sucesso nominal detalhado, `INFO` para marcos operacionais como eventos duplicados, `WARN` para retentativas e indisponibilidades tratadas e `ERROR` para falhas técnicas. Avaliações e publicações bem-sucedidas não geram um `INFO` por item. Nunca registre CPF, token, valores financeiros, corpo de requisição ou payload de evento; use somente identificadores e contexto operacional seguro.

## Pré-requisitos

- Docker Desktop com Docker Compose;
- para executar fora de containers: JDK 21. O Gradle Wrapper baixa uma toolchain compatível quando necessário;
- PowerShell nos exemplos de Windows.

Não é necessário instalar Gradle, PostgreSQL, Kafka ou Keycloak na máquina.

## Roteiro de demonstração local

Este é o roteiro principal para uma demonstração sem conhecimento prévio. Os valores de senha ficam somente em `.env`, que é ignorado pelo Git; nunca copie seu conteúdo para evidências, issue ou chat.

1. Crie o arquivo local de variáveis:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Troque em `.env` os placeholders de senha. O arquivo `.env` é local e não deve ser commitado.

3. Para uma demonstração manual (que preserva dados entre reinícios), gere o artefato e suba os serviços:

   ```powershell
   .\gradlew.bat bootJar --no-daemon
   docker compose up --build
   ```

4. Aguarde os health checks. O Compose só considera o ambiente pronto quando `app`, PostgreSQL, Keycloak e Redpanda estão `healthy`:

   ```powershell
   docker compose ps
   Invoke-RestMethod http://localhost:8080/actuator/health/readiness
   ```

5. Consulte:

   - aplicação/frontend: `http://localhost:8080`;
   - readiness: `http://localhost:8080/actuator/health/readiness`;
   - liveness: `http://localhost:8080/actuator/health/liveness`;
   - Keycloak: `http://localhost:8180`;
   - métricas: `http://localhost:8080/actuator/prometheus`.

6. Execute `./scripts/demo.ps1` para abrir a jornada visual e receber os passos para login, criação, histórico, PDF, evento e métricas.

Para encerrar preservando os volumes, execute `docker compose down`. A remoção de volumes é deliberadamente omitida do fluxo normal porque apaga os dados locais.

### Prova reproduzível a partir de volumes limpos

Para comprovar a pilha de um ambiente limpo, sem reutilizar banco ou broker, execute:

```powershell
./scripts/e2e-compose.ps1
```

Ele verifica `.env`, cria um projeto Compose descartável, remove os volumes antes e depois, espera os health checks em até 240 segundos e roda a jornada automatizada. Consulte os detalhes e a saída TAP em [docs/evidence/compose-e2e.md](docs/evidence/compose-e2e.md). O roteiro não depende de senhas, tokens ou CPFs versionados: os placeholders estão em `.env.example`, as senhas são locais e o CPF de fixture é criado em memória.

### Jornada no navegador

Com o ambiente saudável, siga a ordem abaixo. Não salve o token/senha exibido ou usado no Keycloak.

1. Abra `http://localhost:8080` e entre pelo Keycloak com o usuário demonstrativo e a senha local que você definiu em `.env`.
2. Crie uma avaliação na tela inicial e confira decisão, regras, motivos e `correlationId` devolvido.
3. Abra o histórico, aplique filtros e confirme que o registro persiste.
4. Baixe o PDF pela tela de relatório usando os mesmos filtros e compare decisão, regras e motivos.
5. Observe o evento publicado no Redpanda e as métricas em `http://localhost:8080/actuator/prometheus`; use o `correlationId` para correlacionar sem expor CPF completo.
6. Em caso de falha, execute `docker compose ps`, `docker compose logs app keycloak postgres kafka` e consulte a readiness acima. Verifique se `.env` não manteve nenhum placeholder.

## Executar e testar sem containers

Com PostgreSQL e as demais dependências disponíveis nos endereços configurados:

```powershell
.\gradlew.bat bootRun --no-daemon
.\gradlew.bat test --no-daemon
.\gradlew.bat detekt --no-daemon
node --test "test/*.test.mjs" "src/test/resources/performance/*.test.js" "src/main/resources/static/ts/*.ts"
```

Para verificar a especificação localmente, com a skill `onp-spec-driven` instalada:

```powershell
node C:\Users\brufe\.agents\skills\onp-spec-driven\scripts\onp-spec.mjs verify credito-rotativo
node C:\Users\brufe\.agents\skills\onp-spec-driven\scripts\onp-spec.mjs audit --ci
```

O comando da skill é uma conveniência do ambiente de desenvolvimento; os testes Kotlin e Node continuam executáveis sem ela.

## Autenticação e autorização

A API é um OAuth 2.0 Resource Server. O ambiente local importa o realm `credit-rotativo`; em produção, o emissor deve ser o provedor corporativo. Autorizações:

| Escopo | Operação |
| --- | --- |
| `credit:write` | criar avaliação |
| `credit:read` | consultar avaliação e histórico |
| `credit:report` | gerar PDF |
| `credit:admin` | administração operacional |

Tokens ausentes ou inválidos retornam `401`; tokens válidos sem o escopo necessário retornam `403`. O perfil `prod` exige HTTPS e considera os cabeçalhos do proxy confiável. Nunca grave JWTs, senhas ou CPFs completos em exemplos, logs ou commits.

Para obter um token local de escrita, use a senha definida em `CREDIT_DEMO_PASSWORD` no seu `.env`:

```powershell
$token = Invoke-RestMethod -Method Post -Uri 'http://localhost:8180/realms/credit-rotativo/protocol/openid-connect/token' -ContentType 'application/x-www-form-urlencoded' -Body @{ client_id = 'credit-local'; grant_type = 'password'; username = 'credit-writer'; password = $env:CREDIT_DEMO_PASSWORD }
$headers = @{ Authorization = "Bearer $($token.access_token)"; 'Idempotency-Key' = [guid]::NewGuid().ToString() }
```

O password grant existe somente para facilitar a demonstração local. A evolução corporativa usa Authorization Code com PKCE para canais interativos ou client credentials/workload identity entre serviços; senhas de usuário não transitam pela aplicação.

## Demonstrar a solução

Abra o frontend, envie uma avaliação e observe a explicação de todas as regras. A resposta de criação é `201 Created`; uma decisão de negócio reprovada continua sendo uma resposta válida, não um erro técnico. Em seguida, abra a tela de relatório, aplique os mesmos filtros à lista e ao PDF e baixe o anexo.

Endpoints principais:

| Método e caminho | Resultado esperado |
| --- | --- |
| `POST /api/v1/credit-evaluations` | cria avaliação; exige `Idempotency-Key` |
| `GET /api/v1/credit-evaluations/{evaluationId}` | consulta fotografia da decisão |
| `GET /api/v1/credit-evaluations` | histórico paginado e filtrado |
| `GET /api/v1/credit-evaluations/report.pdf` | relatório PDF com os mesmos filtros |

Toda chamada aceita `X-Correlation-ID`; se ausente ou inválido, a aplicação cria um identificador e o devolve na resposta. Erros seguem um contrato estável com `status`, `code`, `message`, `correlationId`, `path` e, quando aplicável, `fieldErrors`.

## Regras e cálculo demonstrativos

As regras bloqueantes são score mínimo 650, no máximo dois atrasos, limite disponível positivo e comprometimento de até 80%. Crescimento de gastos acima de 20% gera alerta, mas não reprova sozinho. Todas as regras ativas são executadas para produzir explicação completa.

Para clientes elegíveis: `min(limiteDisponível × 70% × fatorDeRisco, R$ 5.000)`. Os fatores são 0,50 para score 650–699, 0,75 para 700–749 e 1,00 para 750–1000. Valores usam duas casas e `HALF_EVEN`.

## Teste de carga

## Contrato OpenAPI

Com a aplicação em execução, consulte o contrato versionado em `http://localhost:8080/openapi/credit-evaluations.yaml` ou a interface Swagger em `http://localhost:8080/swagger-ui/index.html`. Ambos seguem a política de autenticação da aplicação: use uma sessão ou token com o escopo OAuth2 exigido por cada operação. O contrato documenta filtros, paginação, erros correlacionáveis, `Idempotency-Key` e replay; os exemplos usam dados fictícios ou CPF mascarado.

O cenário k6 em `performance/k6/credit-evaluation.js` modela 10.000 avaliações/minuto, p99 abaixo de 1 segundo e menos de 1% de erros técnicos. Instruções e ressalvas estão em [performance/README.md](performance/README.md). O repositório valida o contrato do cenário; resultados de capacidade dependem do ambiente e não são simulados como evidência.

## Limitações e próximos passos

- o frontend é demonstrativo e não substitui um BFF ou design system corporativo;
- o Keycloak e o broker locais são single-node e não representam alta disponibilidade;
- a carga deve ser executada em ambiente isolado com sizing e telemetria reais;
- rotação de chaves, mTLS, WAF, backup/restore e DR pertencem à implantação corporativa;
- a evolução sugerida usa ECS/Fargate, Aurora PostgreSQL, MSK/EventBridge, Secrets Manager, KMS e CloudWatch, detalhada na arquitetura.

O uso de IA e as verificações humanas realizadas estão registrados em [docs/ai-usage.md](docs/ai-usage.md).

## Evidências e escopo da demonstração

O [índice de evidências](docs/evidence/README.md) informa para cada prova o comando, resultado, data e commit validados. Ele separa de forma explícita a execução realmente comprovada das propostas de arquitetura, capacidade e cloud que ainda dependem de um ambiente apropriado.
