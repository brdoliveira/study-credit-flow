# Spec: Diagnóstico operacional por logs

> feature: diagnostico-operacional-logs
> status: pronta

## Contexto

O serviço já propaga `correlationId`, publica métricas e protege respostas HTTP, mas quase não emite logs próprios. Falhas tratadas podem desaparecer sem causa registrada, e os fluxos de outbox e Kafka perdem a correlação fora da thread HTTP. Esta entrega torna falhas diagnosticáveis com logs estruturados e seguros, sem registrar cada avaliação bem-sucedida em `INFO`.

## Histórias

### US-031 — Investigar falhas HTTP pelo identificador de correlação

Como pessoa operadora, quero encontrar a causa técnica de respostas 500 e 503 usando o `correlationId` entregue ao cliente, para diagnosticar incidentes sem expor detalhes internos na API.

#### AC-097 — Falhas HTTP técnicas deixam evidência correlacionada

- **Dado** uma exceção inesperada ou uma dependência indisponível durante uma requisição
- **Quando** o tratamento global produzir a resposta HTTP segura
- **Então** um log `ERROR` ou `WARN` registra código do erro, método, caminho, tipo e frames da exceção com o mesmo `correlationId`, sem corpo da requisição

### US-032 — Acompanhar falhas nos fluxos assíncronos

Como pessoa operadora, quero preservar a correlação em outbox e Kafka, para seguir uma operação depois que ela deixa a thread HTTP.

#### AC-098 — Retentativa da outbox é observável sem expor o evento

- **Dado** uma publicação temporariamente rejeitada pelo broker
- **Quando** a outbox reagendar o evento
- **Então** um log `WARN` correlacionado registra `eventId`, tentativa, próxima execução e tipo da falha, sem serializar o payload

#### AC-099 — Consumo Kafka mantém correlação e resultado operacional

- **Dado** um evento de avaliação recebido do Kafka
- **Quando** ele for processado, duplicado ou falhar
- **Então** o consumidor usa o `correlationId` do evento no contexto de log, registra falhas em `ERROR`, duplicatas em `INFO` e sucesso apenas em `DEBUG`, sempre sem payload

### US-033 — Ingerir logs seguros em ferramentas operacionais

Como pessoa responsável pela plataforma, quero logs JSON com campos estáveis, para pesquisar incidentes no console, CloudWatch ou outra ferramenta sem parsing frágil.

#### AC-100 — Console produz JSON estruturado com identidade do serviço

- **Dado** a aplicação iniciada em qualquer ambiente
- **Quando** um registro for escrito no console
- **Então** a linha usa o formato Logstash JSON e contém timestamp, nível, logger, mensagem, nome, versão e ambiente do serviço, além de campos MDC como `correlationId`, `traceId` e `spanId` quando disponíveis

#### AC-101 — Logs evitam dados sensíveis e volume nominal excessivo

- **Dado** requisições e eventos contendo CPF, token ou valores financeiros
- **Quando** operações técnicas forem registradas
- **Então** nenhum log próprio inclui corpo, payload ou esses valores, e avaliações/publicações bem-sucedidas não geram um registro `INFO` por item

## Fora de escopo

- Adicionar backend, agente ou exportador de tracing distribuído.
- Configurar dashboards, retenção ou alertas específicos de um provedor cloud.
- Registrar payloads HTTP, eventos completos, CPF, tokens ou valores financeiros.
- Alterar contratos HTTP, regras de crédito ou semântica de retry.

## Suposições

Nenhuma. O usuário confirmou a implementação das melhorias recomendadas; o formato JSON usa suporte nativo da versão atual do Spring Boot.

## Perguntas em aberto

Nenhuma.
