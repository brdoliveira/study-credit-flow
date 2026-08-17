# Observabilidade

## Objetivos de serviço

Os objetivos abaixo são janelas operacionais iniciais. Eles devem ser revistos com tráfego e impacto de negócio reais.

| Sinal | Objetivo | Medição |
| --- | --- | --- |
| Disponibilidade HTTP | 99% de respostas sem `5xx` em 30 dias | `http_server_requests_seconds_count` |
| Latência HTTP | p99 abaixo de 1 segundo | `http_server_requests_seconds_bucket` |
| Publicação da outbox | nenhum evento pendente por mais de 60 segundos | `credit_outbox_oldest_pending_age_seconds` |
| Integridade assíncrona | nenhuma falha terminal de outbox ou Kafka | `credit_outbox_failed`, `credit_*_events_total` |

O teste k6 usa os mesmos limites de p99 e erro técnico. Ele comprova o comportamento durante a janela do teste, não o SLO mensal.

## Ambiente local

Preencha `PROMETHEUS_CLIENT_SECRET`, `GRAFANA_ADMIN_USER` e `GRAFANA_ADMIN_PASSWORD` no `.env`. O Prometheus usa client credentials do Keycloak com somente `credit:admin`; o segredo é escrito apenas no filesystem temporário do container.

```bash
./scripts/observability.sh validate
./scripts/observability.sh start
```

Serviços locais, todos vinculados ao loopback:

| Serviço | URL |
| --- | --- |
| Grafana | `http://localhost:3000` |
| Prometheus | `http://localhost:9090` |
| Alertmanager | `http://localhost:9093` |
| Tempo | `http://localhost:3200/ready` |

O dashboard `Credit Flow - Operação` é provisionado automaticamente. Em Grafana Explore, selecione Tempo para consultar traces por serviço, duração, status ou `traceId` registrado nos logs.

Para encerrar a pilha preservando os volumes:

```bash
./scripts/observability.sh stop
```

## Testes de falha

Use estes cenários somente no ambiente local:

```bash
# Readiness deve ficar DOWN e a aplicação deve registrar indisponibilidade.
docker-compose stop postgres
docker-compose start postgres

# A outbox deve acumular/reagendar eventos e disparar alerta de backlog.
docker-compose stop kafka
docker-compose start kafka

# Gera tráfego para os painéis de throughput, latência e erros.
./scripts/run-load-test.sh
```

Depois de cada cenário, confira Prometheus em `Status > Targets`, Alertmanager, o dashboard Grafana e os traces no Tempo. A restauração da dependência deve resolver readiness e alertas após as janelas configuradas.

## Runbooks

### CreditFlowUnavailable

1. Verifique `/actuator/health/readiness`, targets do Prometheus e estado do container.
2. Separe falha de autenticação do scrape de indisponibilidade da aplicação.
3. Consulte PostgreSQL, Kafka e os logs pelo `correlationId`.

### CreditFlowErrorBudgetBurn

1. Agrupe `5xx` por URI e tipo de exceção.
2. Consulte traces lentos ou com erro no Tempo/X-Ray.
3. Reverta a versão recente ou isole a dependência degradada quando houver correlação temporal.

### CreditFlowP99LatencyHigh

1. Compare latência HTTP, duração da avaliação, pool JDBC, CPU e memória.
2. Use traces para localizar banco, autenticação ou serialização dominante.
3. Não aumente timeouts antes de identificar a dependência responsável.

### CreditFlowOutboxTerminalFailure

1. Localize o `eventId` nos logs e confirme o motivo sanitizado no banco.
2. Corrija broker, contrato ou credencial antes do replay.
3. Reprocesse somente o evento identificado e confirme `published` no contador.

### CreditFlowOutboxBacklog

1. Confira disponibilidade do Kafka e taxa de `retry` da outbox.
2. Compare entrada de avaliações com publicações concluídas.
3. Ajuste capacidade apenas se o broker estiver saudável e o backlog continuar crescendo.

### CreditFlowKafkaConsumptionFailures

1. Localize o `eventId` e `correlationId` no log estruturado.
2. Verifique contrato do evento, banco de idempotência e efeito do consumidor.
3. Confirme que o retry não produz efeitos duplicados.

## AWS

O módulo Terraform cria dashboard CloudWatch, alarmes dimensionados para ALB/ECS, filtro de logs `ERROR`, tópico SNS e sidecar ADOT. O aplicativo envia OTLP ao sidecar, que publica traces no X-Ray. Configure `alarm_notification_email` ou conecte `operational_alerts_topic_arn` ao sistema corporativo de incidentes.
