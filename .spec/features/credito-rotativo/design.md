# Design: Crédito rotativo

> feature: credito-rotativo

## Direção arquitetural

A primeira versão será um monólito modular em Kotlin e Spring Boot. A decisão de crédito permanece síncrona; efeitos secundários são desacoplados por Outbox. O desenho privilegia uma implementação pequena o suficiente para o take-home e fronteiras que permitam futura separação em serviços.

```text
Frontend demonstrativo
        |
        v
API REST + OAuth2 Resource Server
        |
        v
EvaluateRevolvingCreditUseCase
   |        |         |
   v        v         v
Regras   Cálculo   Persistência transacional
                       |          |
                       v          v
                  Avaliação     Outbox
                                     |
                                     v
                              Publisher Kafka
                                     |
                                     v
                           Consumidor idempotente
```

## Módulos

### `domain`

- entidades, value objects e enums;
- `CreditRule` e resultados das regras;
- política de consolidação da decisão;
- `CreditLimitCalculator`;
- nenhum acoplamento com Spring, banco, HTTP ou mensageria.

### `application`

- casos de uso de avaliar, consultar e gerar relatório;
- portas de persistência, idempotência, relógio e publicação;
- transações declaradas na fronteira do caso de uso;
- DTOs internos de comando e resultado.

### `infrastructure`

- controllers REST e tratamento de erros;
- Spring Security e mapeamento de escopos;
- JPA/JDBC e migrations Flyway;
- Outbox publisher e consumidor demonstrativo;
- geração do PDF;
- métricas, logs e health checks;
- arquivos estáticos do frontend.

## Contratos HTTP

| Método e rota | Escopo | Sucesso principal |
|---|---|---|
| `POST /api/v1/credit-evaluations` | `credit:write` | `201` nova; `200` replay |
| `GET /api/v1/credit-evaluations` | `credit:read` | `200` paginado |
| `GET /api/v1/credit-evaluations/{id}` | `credit:read` | `200`; `404` ausente |
| `GET /api/v1/credit-evaluations/report.pdf` | `credit:report` | `200 application/pdf` |
| `/actuator/health/liveness` | política operacional | estado do processo |
| `/actuator/health/readiness` | política operacional | prontidão e dependências |
| `/actuator/prometheus` | `credit:admin` | métricas |

Erros usam corpo estável com `status`, `code`, `message`, `correlationId`, `path` e lista opcional de erros por campo. Stack traces e dados sensíveis nunca são devolvidos.

## Dados de entrada

O pedido contém:

- `name`;
- `cpf`;
- `creditScore`;
- `currentInvoiceAmount`;
- `totalLimit`;
- `availableLimit`;
- `latePayments`;
- `monthlySpending` com três valores ordenados do mês mais antigo ao mais recente.

O CPF é validado no limite da API. Respostas usam apenas CPF mascarado. Para correlação persistida, será usado HMAC com segredo externo, não hash simples vulnerável a enumeração.

## Motor de regras

Cada regra implementa um contrato comum e recebe um contexto imutável. Todas as regras ativas executam para preservar explicabilidade completa. Regras `BLOCKING` reprovam; regras `WARNING` enriquecem o resultado sem reprovar sozinhas.

Configurações de thresholds são validadas na inicialização. Toda alteração que modifique comportamento exige nova `ruleSetVersion`.

## Cálculo

O cálculo fica isolado das regras. Usa `BigDecimal`, moeda BRL e parâmetros externalizados. A fórmula definitiva e os fatores de risco dependem da confirmação das suposições ASM-001 a ASM-004.

## Persistência

PostgreSQL será usado localmente e nos testes. Aurora PostgreSQL é o alvo produtivo documentado.

Tabelas principais:

- `credit_evaluation`;
- `credit_evaluation_rule`;
- `credit_idempotency`;
- `credit_outbox`;
- `processed_event` para o consumidor demonstrativo.

Flyway controla o schema. Constraints únicas garantem idempotência e identidade dos eventos. A avaliação, o registro idempotente e a Outbox participam da mesma transação local.

## Idempotência

O hash é calculado sobre uma representação canônica do pedido. A chave não substitui `evaluationId`. Requisições concorrentes são coordenadas por constraint/transação no PostgreSQL, não por memória local.

Estados previstos: `PROCESSING`, `COMPLETED` e `FAILED_RETRYABLE`. A política de retenção depende da confirmação de ASM-010.

## Eventos

Evento inicial: `CreditEvaluationCompleted`.

Envelope mínimo:

- `eventId`;
- `eventType`;
- `eventVersion`;
- `occurredAt`;
- `evaluationId`;
- `decision`;
- `approvedAmount`;
- `ruleSetVersion`;
- `correlationId`.

O evento não contém CPF completo. A entrega é pelo menos uma vez. O publisher usa retry limitado com backoff; o consumidor registra `eventId` antes de considerar o efeito concluído.

## Segurança

A API opera como OAuth2 Resource Server stateless. O ambiente local usa um provedor OIDC em container após confirmação de ASM-006. Em produção, o emissor pode ser corporativo ou Cognito sem alterar domínio e casos de uso.

- `credit:write`: criar avaliação;
- `credit:read`: consultar;
- `credit:report`: gerar PDF;
- `credit:admin`: métricas e operação protegida.

TLS termina no balanceador produtivo. Segredos vêm de variáveis ou secret manager. CORS é limitado à origem configurada. Actuator expõe publicamente apenas o mínimo necessário para health checks.

## Frontend e PDF

O frontend é uma camada demonstrativa fina, sem regra de negócio. A tela de avaliação mostra decisão e regras; a tela de relatório pagina, filtra e reutiliza os filtros ao solicitar o PDF.

O PDF é gerado em memória pelo backend através de uma porta própria. A biblioteca concreta será escolhida e registrada em ADR considerando licença, manutenção, tabelas, fontes e testes.

## Observabilidade e resiliência

- logs estruturados com `correlationId` e `evaluationId`;
- métricas técnicas e de negócio com cardinalidade controlada;
- liveness sem dependências externas;
- readiness considerando PostgreSQL e dependências obrigatórias;
- timeout em comunicação com broker;
- retry apenas para falhas transitórias e fora do caminho síncrono crítico;
- graceful shutdown e processamento seguro da Outbox.

## Testes

- unitários parametrizados para regras e cálculo;
- integração com PostgreSQL via Testcontainers;
- API e segurança com tokens JWT de teste;
- concorrência real para idempotência;
- contrato de evento e duplicidade do consumidor;
- PDF e mascaramento de dados;
- smoke tests do frontend;
- carga reproduzível com k6.

Todo critério de aceite terá ao menos um teste com `@spec:AC-xxx` no título.

## Entrega e cloud

Docker Compose sobe aplicação, PostgreSQL, identidade e broker. O pipeline compila, testa, analisa, audita a spec e gera a imagem somente após os gates.

A arquitetura-alvo recomenda inicialmente ECS/Fargate, Aurora PostgreSQL, CloudWatch/OpenTelemetry e um broker gerenciado escolhido por ADR. EKS e DynamoDB permanecem alternativas justificadas por contexto, não complexidade obrigatória.

## Decisões registradas

- Monólito modular antes de microsserviços.
- Avaliação síncrona; eventos apenas após a decisão.
- PostgreSQL local, em integração e como compatibilidade com Aurora.
- Outbox para evitar dual-write.
- Frontend demonstrativo e PDF no backend.
- Autenticação real por padrões OAuth2/OIDC, sem construir IAM corporativo.

## Governança das decisões

As suposições e perguntas em `spec.md` são parte do contrato. O pacote inicial foi confirmado pelo usuário em 2026-08-15; qualquer mudança posterior deve atualizar a especificação, as tarefas e as provas afetadas.
