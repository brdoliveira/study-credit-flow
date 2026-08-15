# SPEC — Sistema de Regras para Crédito Rotativo

**Contexto:** Take-home técnico — Engenharia de Software Backend  
**Objetivo de uso:** Spec-driven development  
**Stack proposta:** Kotlin + Spring Boot (Java 21), REST, PostgreSQL, Docker  
**Status:** v0.1 — especificação inicial

---

## 1. Objetivo

Construir uma aplicação backend que simule a avaliação e a liberação de crédito rotativo para clientes de cartão de crédito.

A solução deve:

- receber dados fictícios de clientes;
- avaliar regras de elegibilidade;
- calcular o valor máximo de crédito rotativo para clientes elegíveis;
- permitir inclusão de novas regras sem alterar o fluxo principal;
- gerar o resultado de aprovados/reprovados e valores liberados;
- documentar os critérios utilizados na decisão;
- atender requisitos de latência, escalabilidade, segurança, resiliência, observabilidade, manutenibilidade e qualidade.

A implementação não precisa possuir integrações reais com sistemas bancários externos. Dependências externas podem ser simuladas/mocadas.

---

## 2. Escopo funcional obrigatório

### FR-001 — Receber dados do cliente

A aplicação deve receber, no mínimo:

- nome;
- CPF;
- score de crédito;
- valor da fatura atual;
- limite disponível;
- número de atrasos;
- comportamento de gastos nos últimos meses.

### FR-002 — Avaliar elegibilidade

A aplicação deve executar um conjunto de regras de negócio sobre os dados recebidos e retornar uma decisão:

- `APPROVED`
- `REJECTED`

Cada regra deve produzir um resultado explícito contendo:

- código da regra;
- nome da regra;
- status (`PASSED` ou `FAILED`);
- motivo/explicação;
- dados relevantes usados na decisão, sem expor dados pessoais sensíveis.

### FR-003 — Calcular crédito rotativo

Para clientes aprovados, a aplicação deve calcular o valor máximo de crédito rotativo liberado.

O cálculo deve ser separado das regras de elegibilidade para permitir evolução independente.

### FR-004 — Extensibilidade das regras

Novas regras devem poder ser adicionadas sem alteração estrutural do fluxo principal de avaliação.

A implementação deve utilizar um contrato comum de regra, por exemplo:

```kotlin
interface CreditRule {
    fun evaluate(context: CreditEvaluationContext): RuleResult
}
```

A composição das regras pode utilizar Strategy, Chain of Responsibility ou uma coleção ordenada de estratégias.

### FR-005 — Relatório/resultado

A aplicação deve disponibilizar resultado contendo:

- identificador da avaliação;
- identificação mascarada do cliente;
- decisão final;
- valor liberado, quando aprovado;
- lista das regras executadas e seus resultados;
- timestamp;
- duração do processamento.

### FR-006 — Auditoria da decisão

Cada avaliação deve possuir rastreabilidade suficiente para explicar por que foi aprovada ou reprovada.

O sistema deve manter:

- `evaluationId`;
- versão do conjunto de regras;
- regras executadas;
- resultado de cada regra;
- decisão final;
- valor calculado;
- data/hora;
- correlation id.

---

## 3. Regras de negócio

O material do case informa os dados que devem participar da avaliação, mas **não define thresholds numéricos das regras**.

Portanto, a implementação deve tratar os parâmetros das regras como configuráveis e documentar claramente que os valores usados são premissas do take-home.

### 3.1 Regras demonstrativas propostas

Os valores abaixo são **premissas de implementação**, não requisitos fornecidos pelo case.

#### RULE-001 — Score mínimo

Cliente deve possuir score maior ou igual ao limite configurado.

Exemplo de configuração:

```yaml
credit:
  rules:
    minimum-score: 650
```

#### RULE-002 — Quantidade máxima de atrasos

Cliente não pode ultrapassar a quantidade máxima configurada de atrasos.

Exemplo:

```yaml
credit:
  rules:
    max-late-payments: 2
```

#### RULE-003 — Limite disponível

Cliente deve possuir limite disponível maior que zero.

#### RULE-004 — Comprometimento do limite

A relação entre fatura atual e limite total estimado não pode exceder o percentual configurado.

> Caso o modelo não contenha limite total, essa regra deve ser removida ou o dado deve ser incluído explicitamente no contrato.

#### RULE-005 — Comportamento recente de gastos

O comportamento recente deve ser transformado em uma métrica determinística e testável.

Sugestão de modelo:

```json
{
  "monthlySpending": [1200.00, 1350.00, 1280.00]
}
```

A primeira versão pode utilizar média e tendência de gastos, evitando qualquer modelo de ML.

### 3.2 Política de decisão

Por padrão:

- todas as regras classificadas como `BLOCKING` precisam passar;
- falha em qualquer regra `BLOCKING` reprova a avaliação;
- regras futuras podem possuir severidade `WARNING` sem reprovar automaticamente;
- o resultado deve continuar explicável e determinístico.

---

## 4. Cálculo do valor máximo

O case exige o cálculo, mas não fornece uma fórmula.

A fórmula deve ser isolada atrás de um contrato:

```kotlin
interface CreditLimitCalculator {
    fun calculate(context: CreditEvaluationContext): BigDecimal
}
```

### Fórmula demonstrativa proposta

A primeira versão pode calcular o valor liberado como o menor valor entre:

1. percentual configurável do limite disponível;
2. um teto de crédito rotativo configurável;
3. um valor ajustado pelo score/faixa de risco.

Todos os parâmetros devem estar externalizados.

Exemplo:

```yaml
credit:
  calculation:
    available-limit-percentage: 0.70
    maximum-revolving-credit: 5000.00
```

Valores monetários devem utilizar `BigDecimal`.

---

## 5. API REST

### POST `/api/v1/credit-evaluations`

Executa uma avaliação.

#### Request

```json
{
  "name": "Cliente Exemplo",
  "cpf": "12345678909",
  "creditScore": 720,
  "currentInvoiceAmount": 1800.00,
  "availableLimit": 4000.00,
  "latePayments": 0,
  "monthlySpending": [
    1500.00,
    1700.00,
    1800.00
  ]
}
```

#### Response — aprovado

```json
{
  "evaluationId": "018f...",
  "decision": "APPROVED",
  "approvedAmount": 2800.00,
  "ruleSetVersion": "v1",
  "rules": [
    {
      "code": "MINIMUM_SCORE",
      "status": "PASSED",
      "reason": "Credit score satisfies configured threshold"
    }
  ],
  "processedAt": "2026-08-11T22:30:00Z",
  "processingTimeMs": 32
}
```

#### Response — reprovado

```json
{
  "evaluationId": "018f...",
  "decision": "REJECTED",
  "approvedAmount": 0,
  "ruleSetVersion": "v1",
  "rules": [
    {
      "code": "MINIMUM_SCORE",
      "status": "FAILED",
      "reason": "Credit score below configured threshold"
    }
  ],
  "processedAt": "2026-08-11T22:30:00Z",
  "processingTimeMs": 21
}
```

### Validações de entrada

- nome obrigatório;
- CPF obrigatório e validado;
- score dentro de faixa válida;
- valores monetários não negativos;
- número de atrasos não negativo;
- histórico de gastos não pode conter valores negativos;
- payload inválido retorna HTTP `400`;
- violação de regra de negócio não deve ser tratada como erro HTTP: retorna decisão `REJECTED`.

---

## 6. Arquitetura proposta

A primeira versão deve priorizar simplicidade e clareza. A decisão de crédito é síncrona; eventos secundários podem ser processados de forma assíncrona.

```text
Client
  |
  v
REST Controller
  |
  v
Application Service
  |
  +----> Rule Engine
  |        |
  |        +--> CreditRule 1
  |        +--> CreditRule 2
  |        +--> CreditRule N
  |
  +----> CreditLimitCalculator
  |
  +----> Evaluation Repository
  |
  v
Response
```

### Camadas

#### `domain`

Sem dependência de Spring.

Contém:

- entidades/value objects;
- regras;
- contratos;
- decisão;
- resultados das regras;
- calculadora de crédito.

#### `application`

Orquestra os casos de uso.

Exemplo:

- `EvaluateRevolvingCreditUseCase`

Responsabilidades:

1. validar contexto;
2. executar regras;
3. consolidar decisão;
4. calcular valor quando aprovado;
5. persistir auditoria;
6. devolver resultado.

#### `infrastructure`

Contém:

- controllers REST;
- persistência;
- configuração Spring;
- observabilidade;
- adapters externos.

---


### Fluxo híbrido: decisão síncrona + eventos assíncronos

A decisão de crédito deve permanecer no caminho síncrono para que o cliente receba `APPROVED` ou `REJECTED` em até 1 segundo.

Após a decisão ser persistida, eventos secundários podem ser publicados de forma assíncrona por meio de Outbox Pattern, evitando acoplamento entre o fluxo crítico e consumidores como Analytics e Auditoria.

```text
                     ┌──────────────→ Motor de Regras
                     │
Cliente → API ───────┼──────────────→ Cálculo
                     │
                     └──────────────→ Aurora PostgreSQL
                                           │
                                           ↓
                                  APPROVED / REJECTED
                                           │
                                    resposta < 1s
                                           │
                                           ↓
                                      Outbox Event
                                           │
                                           ↓
                                  Kafka / EventBridge
                                      ↙           ↘
                                Analytics       Auditoria
```

#### Caminho síncrono

1. Cliente envia a solicitação para a API.
2. A API valida os dados recebidos.
3. O Motor de Regras avalia a elegibilidade.
4. O componente de Cálculo determina o valor máximo quando aprovado.
5. A decisão e o evento de Outbox são persistidos de forma transacional no Aurora PostgreSQL.
6. A API retorna `APPROVED` ou `REJECTED` dentro do requisito de até 1 segundo.

#### Caminho assíncrono

1. Um publisher lê eventos pendentes da Outbox.
2. O evento é publicado em Kafka/MSK ou EventBridge.
3. Consumidores independentes processam o evento.
4. Analytics pode gerar métricas e análises de negócio.
5. Auditoria pode manter trilha operacional ou alimentar mecanismos específicos de governança.

#### Justificativa

O caminho crítico permanece síncrono porque a decisão precisa ser devolvida rapidamente ao solicitante. Mensageria fica fora do caminho crítico e é utilizada apenas para processamento que não precisa bloquear a resposta.

O Outbox Pattern reduz o risco de persistir uma decisão no banco e falhar antes de publicar o evento. A mudança de estado da avaliação e o registro da Outbox devem ocorrer na mesma transação local.

Kafka/MSK e EventBridge são alternativas de evolução arquitetural. A implementação do take-home pode utilizar uma abstração ou mock de publicação caso não seja necessário provisionar infraestrutura real.


## 7. Persistência

### Estratégia proposta

PostgreSQL para armazenar avaliações e garantir rastreabilidade.

A decisão de crédito deve continuar possível sem consultas adicionais ao banco após o payload estar carregado, reduzindo impacto da persistência na latência.

### Tabelas sugeridas

#### `credit_evaluation`

- `id`
- `cpf_hash`
- `decision`
- `approved_amount`
- `rule_set_version`
- `created_at`
- `processing_time_ms`
- `correlation_id`

#### `credit_evaluation_rule`

- `id`
- `evaluation_id`
- `rule_code`
- `rule_status`
- `reason`

### Dados sensíveis

Não persistir CPF puro sem necessidade.

Para o take-home:

- máscara para apresentação;
- hash para correlação quando necessário;
- nenhum CPF completo em logs.

---

## 8. Requisitos não funcionais

### NFR-001 — Latência

A avaliação deve ocorrer em até **1 segundo** após o recebimento dos dados.

Meta interna proposta:

- p95 < 300 ms;
- p99 < 700 ms;
- máximo funcional < 1 s em cenário nominal.

### NFR-002 — Escalabilidade

A solução deve suportar picos de até **10.000 avaliações por minuto**.

Equivalência aproximada:

- 166,7 avaliações/segundo.

A aplicação deve ser stateless para permitir escalabilidade horizontal.

### NFR-003 — Consistência

Para o mesmo payload, mesma versão de regras e mesma configuração, a decisão deve ser determinística.

Mudanças em regras devem alterar a versão do conjunto de regras.

### NFR-004 — Extensibilidade

Adicionar uma nova regra não deve exigir alteração no serviço principal de avaliação.

### NFR-005 — Disponibilidade e resiliência

Falhas temporárias de dependências não devem causar comportamento silenciosamente incorreto.

Regras:

- definir timeout em toda integração externa;
- utilizar retry apenas para erros transitórios;
- retry com limite + backoff;
- não repetir efeitos não idempotentes;
- health checks para readiness/liveness;
- graceful shutdown;
- falha de persistência deve possuir tratamento explícito.

### NFR-006 — Segurança e compliance

A solução deve considerar:

- LGPD;
- autenticação;
- autorização;
- criptografia em trânsito;
- criptografia em repouso em cenário produtivo;
- princípio de menor privilégio;
- mascaramento de dados;
- ausência de dados financeiros/pessoais completos em logs.

Para o take-home, autenticação pode ser simplificada, desde que a abordagem produtiva seja documentada.

### NFR-007 — Manutenibilidade

- regras pequenas e isoladas;
- baixo acoplamento;
- nomes explícitos;
- configuração externalizada;
- testes automatizados;
- documentação das decisões.

---

## 9. Observabilidade

### Logs

Logs estruturados em JSON.

Campos recomendados:

- `timestamp`;
- `level`;
- `service`;
- `correlationId`;
- `evaluationId`;
- `ruleSetVersion`;
- `decision`;
- `processingTimeMs`.

Nunca registrar:

- CPF completo;
- token;
- credenciais;
- payload financeiro completo.

### Métricas

Obrigatórias:

- `credit_evaluations_total`;
- `credit_evaluations_approved_total`;
- `credit_evaluations_rejected_total`;
- `credit_evaluation_duration_seconds`;
- `credit_rule_failures_total{rule=...}`;
- `credit_evaluation_errors_total`.

Indicadores de negócio:

- taxa de aprovação;
- taxa de reprovação;
- principais motivos de reprovação;
- valor médio aprovado.

Indicadores técnicos:

- p50/p95/p99;
- throughput;
- taxa de erro;
- saturação de threads/conexões;
- disponibilidade.

### Alertas sugeridos

- p99 >= 1 s;
- erro acima do threshold definido;
- aumento abrupto de reprovações;
- indisponibilidade do banco;
- health check falhando.

---

## 10. Estratégia de testes

### Testes unitários

Cada regra deve possuir testes cobrindo:

- limite inferior;
- limite exato;
- limite superior;
- entradas inválidas;
- comportamento esperado.

A calculadora deve possuir testes próprios.

### Testes parametrizados

Priorizar testes parametrizados para regras com thresholds.

### Testes do motor de regras

Validar:

- todas aprovadas;
- uma reprovada;
- múltiplas reprovadas;
- ordem das regras quando relevante;
- inclusão de nova regra sem mudança no motor.

### Testes de integração

Cobrir:

- endpoint REST;
- serialização;
- validações;
- persistência;
- migrations.

Preferência:

- Testcontainers com PostgreSQL.

### Testes de contrato/API

Validar contrato público e códigos HTTP.

### Teste de performance

Executar cenário equivalente ou superior a 10.000 avaliações/minuto.

Ferramentas possíveis:

- k6;
- Gatling;
- JMeter.

Critério mínimo:

- throughput necessário alcançado;
- nenhuma perda de requisição;
- p99 abaixo de 1 segundo em ambiente de teste documentado.

---

### Tela 2 — Relatório

A segunda tela da demo deve consumir o endpoint de listagem de avaliações e permitir:

- visualizar aprovados e reprovados;
- filtrar por decisão;
- filtrar por período;
- visualizar valor liberado;
- abrir detalhes da avaliação;
- clicar em **Gerar PDF**.

O botão deve montar os mesmos filtros aplicados na tela e chamar:

```text
GET /api/v1/credit-evaluations/report.pdf?...filtros...
```

O browser recebe o arquivo gerado pelo backend.


## 11. Relatório em PDF

O relatório funcional do case deve ser gerado em **PDF pelo backend Java/Kotlin**, e não apenas exibido em HTML.

A tela de relatório continua existindo para consulta e filtros, mas deve oferecer uma ação explícita para geração/download do PDF.

### 11.1 Fluxo

```text
Tela de Relatório
HTML + Bootstrap + TypeScript
        |
        | filtros
        v
GET /api/v1/credit-evaluations
        |
        v
Lista de avaliações
        |
        | "Gerar PDF"
        v
GET /api/v1/credit-evaluations/report.pdf
        |
        v
Spring Boot
        |
        ├─ Consulta avaliações no PostgreSQL/Aurora
        ├─ Monta o relatório
        └─ Gera PDF em memória
                |
                v
         application/pdf
```

### 11.2 Endpoint

```http
GET /api/v1/credit-evaluations/report.pdf
```

Filtros opcionais:

```http
GET /api/v1/credit-evaluations/report.pdf?decision=APPROVED
GET /api/v1/credit-evaluations/report.pdf?decision=REJECTED
GET /api/v1/credit-evaluations/report.pdf?from=2026-08-01&to=2026-08-31
```

Resposta:

```http
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="credit-evaluations-report.pdf"
```

### 11.3 Conteúdo mínimo do PDF

O relatório deve conter:

- título do relatório;
- período/filtros utilizados;
- data e hora da geração;
- total de avaliações;
- total de aprovados;
- total de reprovados;
- taxa de aprovação;
- taxa de reprovação;
- tabela de avaliações;
- identificação mascarada do cliente;
- decisão;
- valor liberado;
- data da avaliação;
- `evaluationId`.

### 11.4 Implementação Java/Kotlin

A geração deve acontecer no backend.

Contrato sugerido:

```kotlin
interface CreditEvaluationReportGenerator {
    fun generate(filters: CreditEvaluationReportFilter): ByteArray
}
```

O controller deve responder com `MediaType.APPLICATION_PDF` e `Content-Disposition` de attachment.

A implementação concreta deve gerar o documento em memória e devolver os bytes ao controller.

### 11.5 Biblioteca de PDF

A implementação deve utilizar uma biblioteca Java de geração de PDF.

A escolha concreta deve ser registrada como decisão técnica no README/ADR, considerando:

- licença;
- manutenção;
- simplicidade;
- suporte a tabelas;
- fontes;
- tamanho da dependência;
- facilidade de teste.

A biblioteca não deve ficar acoplada ao domínio. O domínio depende apenas do contrato `CreditEvaluationReportGenerator`.

### 11.6 Segurança

O PDF não deve expor CPF completo ou informações desnecessárias do cliente.

Aplicar:

- CPF mascarado;
- nenhuma credencial/token;
- nenhuma informação sensível em metadata do arquivo;
- autorização no endpoint em cenário produtivo;
- registro de auditoria da geração quando necessário.

### 11.7 Testes do relatório

Adicionar testes para:

- geração de PDF sem filtros;
- geração apenas de aprovados;
- geração apenas de reprovados;
- intervalo de datas;
- relatório vazio;
- caracteres especiais;
- valores monetários;
- CPF mascarado;
- `Content-Type = application/pdf`;
- `Content-Disposition` correto.

O teste deve validar ao menos:

- bytes não vazios;
- assinatura/formato PDF válido;
- conteúdo essencial quando a biblioteca permitir extração;
- comportamento dos filtros.

### 11.8 Critérios de aceite

#### AC-PDF-001

Dadas avaliações existentes, quando o endpoint de relatório for chamado, então deve ser retornado um PDF válido.

#### AC-PDF-002

O PDF deve apresentar aprovados, reprovados e respectivos valores liberados.

#### AC-PDF-003

Filtros aplicados na tela devem poder ser reutilizados na geração do PDF.

#### AC-PDF-004

CPF deve aparecer apenas mascarado no PDF.

#### AC-PDF-005

O relatório deve ser gerado pelo backend Java/Kotlin e entregue como `application/pdf`.


## 11. Tratamento de erros

Formato padrão:

```json
{
  "code": "INVALID_REQUEST",
  "message": "Request validation failed",
  "correlationId": "..."
}
```

Categorias:

- `INVALID_REQUEST` → 400;
- `UNAUTHORIZED` → 401;
- `FORBIDDEN` → 403;
- `INTERNAL_ERROR` → 500;
- `DEPENDENCY_UNAVAILABLE` → 503.

Reprovação de crédito **não é erro técnico**.

---

## 12. Concorrência e idempotência

A idempotência faz parte da arquitetura da primeira versão e deve ser aplicada nos pontos em que retries ou reentregas possam gerar efeitos duplicados.

A avaliação das regras é determinística para o mesmo payload, mesma configuração e mesma versão de regras. Ainda assim, retries do cliente podem gerar avaliações, registros ou eventos duplicados. Por isso, a API deve aceitar uma chave de idempotência.

### 12.1 Idempotência da API

Endpoint:

```http
POST /api/v1/credit-evaluations
Idempotency-Key: <uuid>
```

Fluxo:

```text
Cliente
   |
   | Idempotency-Key
   v
API
   |
   |-- calcula request_hash
   |
   |-- chave inexistente -------------------------┐
   |                                              |
   |                                      Motor de Regras
   |                                              |
   |                                           Cálculo
   |                                              |
   |                                              v
   |                                           Aurora
   |                                      ┌───────┼────────┐
   |                                      │       │        │
   |                                  Evaluation Idempotency Outbox
   |                                      │
   |                                      v
   |                                   Response
   |
   |-- mesma chave + mesmo hash --> retorna resultado anterior
   |
   └-- mesma chave + hash diferente --> HTTP 409 Conflict
```

Regras:

1. O cliente envia `Idempotency-Key`.
2. A aplicação calcula um hash canônico do payload.
3. Se a chave ainda não existir, a avaliação é executada.
4. Resultado da avaliação, chave de idempotência e evento de Outbox devem ser persistidos de forma consistente.
5. Se a mesma chave for recebida novamente com o mesmo hash, o resultado anterior deve ser retornado sem executar novamente os efeitos persistentes.
6. Se a mesma chave for recebida com payload diferente, retornar `409 Conflict`.
7. Deve existir constraint única para a chave de idempotência.
8. Requisições concorrentes com a mesma chave devem ser tratadas pela constraint/transação, não apenas por verificação em memória.

Tabela sugerida:

```text
credit_idempotency
------------------
idempotency_key    UNIQUE
request_hash
evaluation_id
created_at
```

A chave não deve ser usada como substituta do `evaluationId`; são identificadores com responsabilidades diferentes.

### 12.2 Idempotência da Outbox

Cada evento da Outbox deve possuir um `eventId` único.

Exemplo:

```text
credit_outbox
-------------
event_id            UNIQUE
aggregate_id
event_type
payload
status
created_at
published_at
```

O publisher pode sofrer retry. Portanto, a arquitetura assume entrega `at-least-once`: o mesmo evento pode eventualmente ser publicado mais de uma vez.

O objetivo do Outbox é evitar o dual-write entre persistência da decisão e publicação do evento. A avaliação e o registro da Outbox devem ser gravados na mesma transação local.

Não deve ser afirmado que o Outbox sozinho garante exactly-once.

### 12.3 Consumidores idempotentes

Consumidores de Kafka/MSK ou EventBridge devem assumir possibilidade de reentrega.

Para consumidores que executem efeitos que não podem ser duplicados, registrar o `eventId` processado.

Exemplo:

```text
processed_event
---------------
consumer_name
event_id
processed_at

UNIQUE(consumer_name, event_id)
```

Fluxo:

```text
Outbox
   |
   v
Kafka / EventBridge
   |
   v
Consumer
   |
   |-- eventId já processado? --> ignora/ack
   |
   └-- eventId novo
          |
          ├─ executa efeito
          └─ registra eventId
```

Quando possível, o efeito do consumidor e o registro de processamento devem participar da mesma transação local.

### 12.4 O que a idempotência protege

A repetição do cálculo das regras, isoladamente, não é necessariamente problemática. O risco está nos efeitos associados à avaliação.

A estratégia protege contra:

- criação duplicada de avaliações por retry;
- eventos duplicados gerando efeitos repetidos;
- notificações duplicadas;
- duplicidade em integrações futuras;
- inconsistência causada por retries após timeout.

### 12.5 Critérios de aceite de idempotência

#### AC-IDEMP-001

Dada uma requisição válida com uma nova `Idempotency-Key`, quando a avaliação for executada, então uma única avaliação deve ser persistida.

#### AC-IDEMP-002

Dada uma requisição já processada, quando a mesma `Idempotency-Key` e o mesmo payload forem enviados novamente, então o resultado anterior deve ser retornado sem criar nova avaliação.

#### AC-IDEMP-003

Dada uma `Idempotency-Key` existente, quando ela for reutilizada com payload diferente, então a API deve retornar HTTP `409 Conflict`.

#### AC-IDEMP-004

Duas requisições concorrentes com a mesma `Idempotency-Key` não podem produzir duas avaliações persistidas.

#### AC-IDEMP-005

Cada evento da Outbox deve possuir `eventId` único.

#### AC-IDEMP-006

Um consumidor idempotente não deve repetir seu efeito quando receber novamente um `eventId` já processado.

### 12.6 Testes obrigatórios

Adicionar testes para:

- primeira requisição com chave nova;
- retry com mesma chave e mesmo payload;
- mesma chave com payload diferente;
- duas requisições concorrentes com a mesma chave;
- constraint única no banco;
- retry do publisher da Outbox;
- recebimento duplicado do mesmo `eventId` pelo consumidor.

## 13. Deploy e execução

### Local

A aplicação deve iniciar com:

```bash
docker compose up
```

Componentes mínimos:

- aplicação;
- PostgreSQL.

### Health checks

- `/actuator/health/liveness`
- `/actuator/health/readiness`

### Configuração

Nenhum segredo deve estar hardcoded.

Usar:

- environment variables;
- `application.yml`;
- secret manager em arquitetura produtiva.

---

## 14. Arquitetura cloud proposta

Esta seção representa uma **evolução produtiva**, não a implementação mínima obrigatória.

```text
Internet / Internal Channel
        |
        v
      ALB
        |
        v
 ECS/Fargate or EKS
        |
        +------> Aurora PostgreSQL
        |
        +------> CloudWatch / OpenTelemetry
```

### ECS x EKS

Para este serviço isolado, ECS/Fargate é uma escolha inicial mais simples.

EKS faria sentido quando houver:

- plataforma Kubernetes já padronizada;
- múltiplos workloads;
- necessidade do ecossistema Kubernetes;
- políticas/operadores específicos.

A apresentação deve destacar esse trade-off em vez de escolher EKS apenas por complexidade.

### Aurora PostgreSQL

Adequado para:

- auditoria;
- consistência transacional;
- histórico das avaliações.

### Escalabilidade

- serviço stateless;
- múltiplas tasks/pods;
- autoscaling por CPU, memória e/ou latência;
- ALB distribuindo tráfego;
- múltiplas AZs em produção.

---

## 15. Decisões arquiteturais

### ADR-001 — Motor de regras baseado em Strategy

**Decisão:** cada regra implementará um contrato comum.

**Motivo:** permite incluir/remover regras sem alterar o fluxo de avaliação.

**Trade-off:** para regras extremamente dinâmicas, um rules engine externo pode oferecer maior flexibilidade, porém adicionaria complexidade desnecessária para o escopo atual.

### ADR-002 — Monólito modular

**Decisão:** uma aplicação Spring Boot modular.

**Motivo:** o domínio do case não justifica microsserviços na primeira versão.

**Trade-off:** microsserviços permitiriam deploy/escala independentes, mas aumentariam complexidade operacional, observabilidade, consistência distribuída e custo.

### ADR-003 — PostgreSQL

**Decisão:** PostgreSQL para auditoria das decisões.

**Motivo:** dados estruturados e necessidade de consistência/rastreabilidade.

**Trade-off:** DynamoDB poderia oferecer escala horizontal e baixa latência com access patterns definidos, porém não é necessário para o volume informado e aumentaria a complexidade de modelagem do take-home.

### ADR-004 — Processamento síncrono

**Decisão:** avaliação síncrona via REST.

**Motivo:** requisito de resposta em até 1 segundo ao usuário.

**Trade-off:** mensageria poderia ser utilizada para tarefas posteriores, como analytics, auditoria secundária ou notificações, mas não deve estar no caminho crítico da decisão.

---

## 16. Critérios de aceite

### AC-001

Dado um cliente válido, quando a API de avaliação for chamada, então todas as regras configuradas devem ser executadas.

### AC-002

Dado que uma regra bloqueante falhe, então a decisão deve ser `REJECTED`.

### AC-003

Dado que todas as regras bloqueantes passem, então a decisão deve ser `APPROVED` e o valor máximo deve ser calculado.

### AC-004

Dada uma nova implementação de `CreditRule`, quando ela for registrada no container, então o fluxo principal deve executá-la sem alteração em seu código.

### AC-005

Toda resposta deve possuir `evaluationId`, decisão, versão de regras e duração.

### AC-006

Nenhum log deve conter CPF completo.

### AC-007

Em teste de carga documentado, a aplicação deve demonstrar capacidade para 10.000 avaliações/minuto mantendo o requisito de latência de até 1 segundo.

### AC-008

As principais regras e a calculadora devem possuir cobertura de testes para cenários de aprovação, reprovação e limites.

### AC-009

A solução deve possuir README suficiente para execução local sem conhecimento prévio do projeto.

### AC-010

A API deve implementar `Idempotency-Key` com persistência e constraint única.

### AC-011

Retry com a mesma chave e o mesmo payload deve retornar a avaliação original.

### AC-012

Reutilização da mesma chave com payload diferente deve retornar `409 Conflict`.

### AC-013

Eventos assíncronos devem possuir `eventId` único e consumidores com efeitos não duplicáveis devem ser idempotentes.

---

## 17. Estrutura de projeto sugerida

```text
src/main/kotlin/.../
├── domain/
│   ├── model/
│   ├── rule/
│   ├── calculation/
│   └── repository/
├── application/
│   └── usecase/
└── infrastructure/
    ├── web/
    ├── persistence/
    ├── config/
    └── observability/
```

---

## 18. README obrigatório

O README final deve conter:

1. problema;
2. requisitos atendidos;
3. arquitetura;
4. como executar;
5. exemplos de requests;
6. regras implementadas;
7. premissas adotadas;
8. estratégia de testes;
9. teste de performance;
10. observabilidade;
11. segurança;
12. decisões e trade-offs;
13. limitações;
14. possíveis evoluções;
15. uso de IA.

---

## 19. Uso de Inteligência Artificial

O case permite IA, mas exige transparência.

Adicionar ao README:

### Como a IA foi utilizada

Exemplo:

- apoio na estruturação inicial da especificação;
- revisão de alternativas arquiteturais;
- geração de casos de teste candidatos;
- revisão de documentação.

### Como a qualidade foi garantida

- todas as decisões foram revisadas pelo candidato;
- código gerado foi compreendido e validado;
- testes automatizados verificam comportamento;
- decisões técnicas são defendidas independentemente da IA.

Não apresentar código ou decisão que não possa ser explicado na entrevista.

---

## 20. Fora do escopo da primeira versão

- integração com bureau de crédito real;
- integração com core bancário;
- machine learning;
- decisão baseada em modelo estatístico;
- múltiplos microsserviços;
- Kafka/MSK no caminho crítico;
- Kubernetes obrigatório;
- interface web;
- autenticação corporativa real;
- processamento de cartão real.

Esses itens podem ser discutidos como evolução.

---

## 21. Evoluções futuras

- versionamento dinâmico de regras;
- painel para configuração de thresholds;
- feature flags;
- publicação assíncrona de `CreditEvaluationCompleted`;
- Outbox Pattern para publicação confiável de eventos;
- Kafka/MSK ou EventBridge para analytics e integrações;
- cache de dados auxiliares;
- motor de regras externo caso o negócio exija alterações frequentes sem deploy;
- rate limiting;
- WAF;
- OAuth2/OIDC;
- trilha de auditoria imutável;
- dashboards operacionais;
- chaos/resilience tests.

---

## 22. Ordem de implementação recomendada para Spec-Driven Development

### Iteração 1 — domínio

Implementar:

- modelos;
- `CreditRule`;
- `RuleResult`;
- `CreditDecision`;
- `CreditLimitCalculator`;
- testes unitários.

### Iteração 2 — caso de uso

Implementar:

- motor/composição das regras;
- `EvaluateRevolvingCreditUseCase`;
- consolidação do resultado;
- testes.

### Iteração 3 — API

Implementar:

- DTOs;
- validações;
- controller;
- exception handler;
- testes de API.

### Iteração 4 — persistência, auditoria e idempotência

Implementar:

- PostgreSQL/Aurora PostgreSQL;
- migrations;
- repository;
- persistência de `Idempotency-Key` + `request_hash`;
- constraints únicas;
- persistência transacional de avaliação + Outbox;
- `eventId` único;
- testes de concorrência/idempotência;
- Testcontainers.

### Iteração 5 — operação

Implementar:

- logs estruturados;
- métricas;
- Actuator;
- health checks;
- correlation id.

### Iteração 6 — entrega e relatório

Implementar:

- tela de relatório;
- endpoint de listagem com filtros;
- geração do relatório em PDF no backend Java/Kotlin;
- botão `Gerar PDF`;
- testes do relatório;
- Dockerfile;
- Docker Compose;
- testes de performance;
- README;
- diagramas;
- documentação de trade-offs;
- seção de uso de IA.

---

## 23. Definition of Done

Uma feature só é considerada concluída quando:

- atende aos critérios de aceite;
- possui testes automatizados;
- não introduz dados sensíveis em logs;
- está documentada quando altera contrato ou arquitetura;
- respeita os limites de dependência entre camadas;
- passa no build local;
- mantém a API compatível;
- possui tratamento de erro coerente;
- pode ser explicada tecnicamente na apresentação.

