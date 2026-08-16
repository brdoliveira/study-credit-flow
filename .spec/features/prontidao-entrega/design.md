# Design: Prontidão de entrega

> feature: prontidao-entrega

## Objetivo arquitetural

Conectar os componentes existentes em um fluxo executável e substituir provas estáticas por testes nas fronteiras reais. A arquitetura continua como monólito modular; esta feature adiciona adapters e automação, não novos microsserviços.

## Fluxo interativo recomendado

```text
navegador → Spring Security BFF → Keycloak OIDC
          → sessão HttpOnly + CSRF
          → controllers / PDF
```

O backend usa OAuth2 Login para o navegador e mantém Resource Server JWT para consumidores máquina a máquina. Autoridades são normalizadas no mesmo modelo `credit:*`. Recursos estáticos e início/callback de login têm política explícita; APIs não transformam `401` em redirect HTML.

## Fluxo assíncrono

```text
API → transação PostgreSQL (avaliação + outbox)
    → scheduler com lock/skip locked
    → KafkaTemplate/Redpanda
    → listener
    → transação PostgreSQL (eventId + efeito)
```

O publisher seleciona lotes concorrentes com segurança, confirma somente após ack e persiste retry. O listener reconhece duplicatas. Testcontainers executa PostgreSQL e Kafka reais; mocks ficam restritos a testes unitários de falhas específicas.

## Semântica idempotente

O repositório retorna um resultado composto com corpo e origem (`CREATED` ou `REPLAYED`). O controller traduz para `201` ou `200` e inclui um cabeçalho estável de replay. A transação engloba reserva da chave, avaliação, Outbox e resposta idempotente.

## Observabilidade

O caso de uso ou decorator registra decisão, duração e falhas por regra. O tratamento de exceções registra erro técnico uma única vez. Readiness consulta PostgreSQL e conectividade com o broker; liveness não consulta dependências. Probes mínimos são públicos no Compose e métricas permanecem restritas.

## Provas de integração

- Spring Boot + PostgreSQL + Kafka via Testcontainers para persistência, Outbox, publisher e consumer.
- Navegador automatizado contra Docker Compose para login, criação, consulta e PDF.
- k6 real produz resumo JSON sanitizado e metadados do ambiente.
- CI executa testes, `verify`, `audit --ci`, scans, SBOM e somente então constrói a imagem.

## Infraestrutura como código

Terraform de referência é modular e não executa `apply` na CI. `fmt`, `validate` e scanner de IaC verificam rede privada, ECS/Fargate, Aurora, integração assíncrona, segredos, criptografia, logs, alarmes e autoscaling. Valores locais e credenciais nunca são versionados.

## Decisões pendentes

- A confirmação do BFF está registrada em Q-004.
- O ambiente aceito para a prova de carga está registrado em Q-005.

