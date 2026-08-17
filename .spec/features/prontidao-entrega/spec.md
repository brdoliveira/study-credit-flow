# Spec: Prontidão de entrega e integração real

> feature: prontidao-entrega
> status: rascunho

## Contexto

A primeira entrega implementou domínio, API, persistência, segurança, frontend, PDF, mensageria e observabilidade com rastreabilidade completa. A revisão de prontidão encontrou uma diferença entre possuir os componentes e demonstrar que eles funcionam juntos em runtime. Esta feature fecha essa diferença e transforma a solução em uma demonstração ponta a ponta reproduzível para a vaga de Engenharia de Sistemas Backend.

O foco é provar comportamento real: autenticação interativa, semântica HTTP idempotente, Outbox com Kafka, métricas alimentadas pelo fluxo, Docker Compose saudável, carga executada, CI reproduzível, contrato OpenAPI e infraestrutura AWS validável sem criar recursos pagos.

## Histórias

### US-013 — Autenticar o operador pelo navegador

Como operador autorizado, quero entrar e sair da aplicação pelo provedor de identidade, para usar o frontend sem manipular tokens manualmente.

#### AC-048 — Acesso interativo inicia o login corporativo

- **Dado** um navegador sem sessão autenticada
- **Quando** o operador acessa a tela de avaliação ou relatório
- **Então** a aplicação redireciona para o provedor OIDC e conclui o callback em uma sessão autenticada
- **E** retorna o operador para a página originalmente solicitada

#### AC-049 — Tokens não ficam expostos ao JavaScript

- **Dado** um operador autenticado pelo fluxo Authorization Code com PKCE
- **Quando** o frontend chama a API ou baixa o PDF
- **Então** a aplicação usa uma sessão segura `HttpOnly`, `Secure` em produção e `SameSite`
- **E** access token, refresh token e segredo de cliente não aparecem em HTML, JavaScript, `localStorage`, `sessionStorage`, URL ou logs
- **E** operações mutáveis possuem proteção CSRF compatível com o BFF

#### AC-050 — Permissões controlam telas e operações

- **Dado** operadores com permissões distintas de escrita, leitura, relatório e administração
- **Quando** cada operador navega e chama as operações protegidas
- **Então** somente as telas e ações correspondentes às suas permissões ficam disponíveis
- **E** uma API sem autenticação responde `401` e uma permissão insuficiente responde `403` com contrato correlacionável

#### AC-051 — Logout encerra a sessão local

- **Dado** um operador autenticado
- **Quando** ele solicita logout
- **Então** a sessão local é invalidada, cookies são removidos e recursos protegidos deixam de ser acessíveis
- **E** o redirecionamento pós-logout usa uma URL previamente permitida

#### AC-052 — O mesmo emissor funciona dentro e fora dos containers

- **Dado** Keycloak e aplicação executando no Docker Compose
- **Quando** o navegador obtém um token pelo endereço público e a aplicação valida o token pela rede interna
- **Então** descoberta OIDC, `issuer` e acesso de backchannel são compatíveis
- **E** um token emitido pelo fluxo local é aceito pela aplicação sem desabilitar validação de emissor ou assinatura

### US-014 — Repetir chamadas com semântica HTTP correta

Como consumidor da API, quero distinguir criação de replay, para tratar retentativas sem interpretar o mesmo recurso como recém-criado.

#### AC-053 — Primeira execução retorna criação

- **Dado** uma chave de idempotência válida ainda não utilizada e um pedido válido
- **Quando** a avaliação é criada
- **Então** a API responde `201 Created`, envia `Location` e informa que a resposta não é replay

#### AC-054 — Replay retorna sucesso sem nova criação

- **Dado** uma chave concluída e o mesmo payload canônico
- **Quando** a chamada é repetida
- **Então** a API responde `200 OK` com o mesmo corpo e `evaluationId`
- **E** um indicador estável informa que a resposta foi reproduzida
- **E** nenhuma avaliação nem evento adicional é criado

#### AC-055 — Concorrência e conflito são provados pela API real

- **Dado** chamadas HTTP concorrentes com a mesma chave
- **Quando** usam payloads iguais ou divergentes contra PostgreSQL real
- **Então** payloads iguais convergem para uma criação e um replay do mesmo recurso
- **E** payload divergente responde `409 Conflict`
- **E** existe uma única avaliação e um único evento de Outbox

### US-015 — Publicar e consumir eventos no runtime

Como sistema consumidor, quero receber eventos reais de avaliações concluídas, para integrar efeitos assíncronos com entrega pelo menos uma vez e processamento idempotente.

#### AC-056 — Avaliação e Outbox são confirmadas atomicamente no PostgreSQL

- **Dado** uma avaliação criada pela API
- **Quando** a transação é confirmada ou revertida
- **Então** avaliação e evento pendente existem juntos após commit
- **E** nenhum dos dois permanece após rollback
- **E** a prova consulta as tabelas em PostgreSQL real

#### AC-057 — Publisher envia o evento ao broker e confirma a Outbox

- **Dado** um evento pendente no PostgreSQL e Kafka compatível disponível
- **Quando** o publisher agendado processa a Outbox
- **Então** publica no tópico versionado usando `eventId` como identidade estável
- **E** marca o evento como publicado somente após confirmação do broker

#### AC-058 — Falha transitória persiste tentativa e executa retry

- **Dado** um evento pendente e uma indisponibilidade transitória do broker
- **Quando** a publicação falha
- **Então** o PostgreSQL registra tentativa, erro sanitizado e próxima execução com backoff limitado
- **E** o evento permanece publicável e é confirmado após a recuperação do broker

#### AC-059 — Consumidor Kafka aplica o efeito uma única vez

- **Dado** duas entregas do mesmo `eventId`
- **Quando** o listener consome ambas
- **Então** o efeito e o registro de processamento são confirmados na mesma transação local
- **E** a segunda entrega é reconhecida sem repetir o efeito

#### AC-060 — Evento ponta a ponta preserva contrato e privacidade

- **Dado** uma avaliação criada pelo endpoint autenticado
- **Quando** seu evento percorre Outbox, Kafka e consumidor
- **Então** o payload mantém versão, `evaluationId`, decisão, valor, versão das regras, data e `correlationId`
- **E** não contém CPF completo, token, senha ou dados internos de exceção

### US-016 — Observar o comportamento real da aplicação

Como equipe de operação, quero métricas e health checks alimentados pelo runtime, para detectar degradação e diagnosticar o serviço.

#### AC-061 — Avaliações alimentam métricas de negócio e duração

- **Dado** avaliações aprovadas, reprovadas e regras falhas processadas pela API
- **Quando** as métricas são consultadas
- **Então** os contadores de decisão, throughput e falha por regra refletem as operações executadas
- **E** a duração ponta a ponta é registrada sem identificadores de alta cardinalidade

#### AC-062 — Erros técnicos alimentam métricas operacionais

- **Dado** uma falha interna ou indisponibilidade de dependência
- **Quando** o tratamento global produz `500` ou `503`
- **Então** o contador técnico correspondente é incrementado uma vez
- **E** logs e métricas compartilham o `correlationId` sem expor payload financeiro ou stack trace na resposta

#### AC-063 — Readiness verifica dependências realmente obrigatórias

- **Dado** a aplicação em execução
- **Quando** PostgreSQL ou broker necessário fica indisponível
- **Então** readiness fica indisponível e identifica a dependência
- **E** liveness continua representando apenas a vida do processo

#### AC-064 — Endpoints operacionais têm política de acesso explícita

- **Dado** health checks usados pelos containers e métricas destinadas à operação
- **Quando** são acessados
- **Então** liveness e readiness possuem acesso mínimo suficiente para probes sem sessão interativa
- **E** Prometheus e demais endpoints sensíveis exigem permissão administrativa ou rede operacional confiável

### US-017 — Demonstrar o ambiente completo localmente

Como avaliador do processo seletivo, quero subir e usar toda a solução com comandos documentados, para verificar a integração sem ajustes manuais ocultos.

#### AC-065 — Docker Compose fica saudável a partir de um ambiente limpo

- **Dado** Docker disponível, imagens ausentes e variáveis locais válidas
- **Quando** o build e o `docker compose up` são executados a partir de volumes limpos
- **Então** aplicação, PostgreSQL, Keycloak e broker atingem estado saudável dentro do tempo documentado
- **E** migrations, realm, tópico e usuários demonstrativos ficam prontos de forma determinística

#### AC-066 — Jornada visual funciona ponta a ponta

- **Dado** o ambiente completo saudável
- **Quando** um operador entra pelo Keycloak, cria uma avaliação, consulta o histórico e baixa o PDF
- **Então** todas as etapas terminam com sucesso pelo navegador
- **E** decisão, regras, motivos, filtros, PDF e correlação correspondem aos dados persistidos

#### AC-067 — Execução local não depende de segredos versionados

- **Dado** um clone novo do repositório
- **Quando** a configuração local é preparada
- **Então** segredos vêm de arquivo ignorado ou variáveis de ambiente
- **E** exemplos, fixtures, imagens, logs e evidências não contêm credenciais, tokens nem CPF completo

### US-018 — Comprovar a capacidade nominal

Como responsável técnico, quero executar e guardar o resultado do ensaio de carga, para sustentar a meta de capacidade com evidência reproduzível.

#### AC-068 — Ensaio nominal atinge os objetivos

- **Dado** ambiente isolado, aquecido e dimensionado conforme documentação
- **Quando** o k6 envia ao menos 10.000 avaliações por minuto durante cinco minutos
- **Então** o p99 da fase nominal fica abaixo de 1 segundo
- **E** erros técnicos ficam abaixo de 1% e não há `dropped_iterations`

#### AC-069 — Evidência de carga é rastreável

- **Dado** uma execução concluída do k6
- **Quando** o resultado é arquivado
- **Então** a evidência informa commit, data, ambiente, recursos, configuração, taxa observada, p99, erros, iterações descartadas e resultado dos thresholds
- **E** o arquivo não contém token, CPF completo ou payload sensível

#### AC-070 — Reprovação de negócio não é contada como erro técnico

- **Dado** respostas de crédito aprovadas e reprovadas durante o ensaio
- **Quando** as métricas do k6 são calculadas
- **Então** somente falhas de transporte e respostas `5xx` alimentam a taxa de erro técnico
- **E** respostas válidas `2xx` são contabilizadas como processamento concluído

### US-019 — Executar gates reproduzíveis na integração contínua

Como equipe de engenharia, quero gates versionados e independentes do ambiente pessoal, para impedir que uma alteração inválida gere imagem publicável.

#### AC-071 — CI verifica e audita a spec com ferramenta versionada

- **Dado** um checkout limpo no GitHub Actions
- **Quando** o job de qualidade executa
- **Então** o mesmo motor versionado no repositório executa `verify` e `audit --ci`
- **E** não depende de skill local, pacote `latest` ou instalação `npx --yes` não fixada

#### AC-072 — Integração real bloqueia a construção da imagem

- **Dado** uma mudança no backend, segurança, banco ou mensageria
- **Quando** o pipeline executa antes do build da imagem
- **Então** testes unitários, PostgreSQL real, Kafka real e jornada HTTP crítica precisam passar
- **E** qualquer falha impede o job de imagem

#### AC-073 — Supply chain produz gates e inventário

- **Dado** código, dependências e imagem candidatos à entrega
- **Quando** o pipeline de segurança executa
- **Então** detecta segredo versionado e vulnerabilidade acima da severidade definida
- **E** gera SBOM versionada como artefato do pipeline
- **E** nenhum gate crítico usa `continue-on-error`

### US-020 — Publicar um contrato de API verificável

Como consumidor da API, quero documentação OpenAPI atualizada, para integrar sem depender da leitura do código-fonte.

#### AC-074 — OpenAPI descreve operações e contratos relevantes

- **Dado** a aplicação em execução
- **Quando** o contrato OpenAPI é consultado
- **Então** documenta endpoints, modelos, filtros, paginação, status HTTP, erros, `Idempotency-Key`, replay, correlação e escopos OAuth2
- **E** exemplos usam dados fictícios e mascarados

#### AC-075 — Contrato OpenAPI acompanha o comportamento

- **Dado** mudanças nos controllers ou DTOs
- **Quando** os testes de contrato executam
- **Então** divergências relevantes entre OpenAPI e comportamento HTTP falham o build
- **E** a interface de documentação segue a mesma política de acesso definida para o ambiente

### US-021 — Validar a arquitetura AWS sem criar recursos pagos

Como arquiteto responsável, quero infraestrutura como código validável, para demonstrar automação e decisões cloud sem provisionar uma conta real.

#### AC-076 — Infraestrutura como código representa a arquitetura proposta

- **Dado** os módulos de referência versionados
- **Quando** formatação e validação estática são executadas
- **Então** representam rede privada, entrada protegida, ECS/Fargate, Aurora PostgreSQL, broker/eventos, Secrets Manager/KMS, observabilidade e autoscaling
- **E** as saídas e variáveis permitem entender limites, dependências e custos principais

#### AC-077 — Configuração cloud aplica defaults seguros

- **Dado** a configuração de referência para AWS
- **Quando** scanners de infraestrutura analisam o plano sem executar `apply`
- **Então** não há banco ou tasks diretamente públicos, segredos em texto, armazenamento sem criptografia ou logs desabilitados
- **E** backup, alta disponibilidade, least privilege e estratégia de migrations estão documentados

### US-022 — Entregar evidências reproduzíveis

Como avaliador técnico, quero um roteiro curto e evidências indexadas, para reproduzir e defender a solução durante a entrevista.

#### AC-078 — README conduz a demonstração completa

- **Dado** uma pessoa com os pré-requisitos instalados e sem contexto anterior
- **Quando** ela segue o roteiro principal
- **Então** consegue configurar segredos locais, subir o ambiente, autenticar, avaliar, consultar, gerar PDF, observar evento e métricas e encerrar os serviços
- **E** comandos de diagnóstico e problemas conhecidos estão documentados

#### AC-079 — Evidências identificam a revisão validada

- **Dado** testes, carga, scans e validações concluídos
- **Quando** o índice de evidências é consultado
- **Então** cada evidência informa comando, resultado, data e commit validado
- **E** fica explícito o que foi executado de verdade e o que é apenas arquitetura proposta

## Fora de escopo

- Integração com o provedor corporativo real da instituição financeira de referência, MFA, SCIM ou diretório de funcionários.
- Provisionamento efetivo de recursos pagos na AWS ou publicação de imagem em registry externo.
- Garantia de entrega exatamente uma vez; permanece entrega pelo menos uma vez com consumidor idempotente.
- Frontend produtivo completo, design system corporativo ou aplicação móvel.
- Teste de desastre real multi-região; backup e recuperação serão representados e documentados.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-011 | A autenticação interativa será implementada como BFF no Spring Security, usando Authorization Code com PKCE, sessão segura e tokens fora do JavaScript. | confirmada | Confirmado pelo usuário em 2026-08-16. |
| ASM-012 | O Keycloak local continuará sendo o provedor OIDC e o Redpanda continuará sendo o broker Kafka compatível do Compose. | confirmada | Mantém as escolhas já confirmadas na feature crédito-rotativo. |
| ASM-013 | A evidência de 10.000 avaliações/minuto poderá ser produzida em ambiente isolado reproduzível, não necessariamente no notebook usado para desenvolvimento. | confirmada | Confirmado pelo usuário em 2026-08-16. |
| ASM-014 | A infraestrutura AWS será entregue como referência Terraform validável e escaneável, sem `apply` e sem custo de nuvem. | confirmada | Preserva o fora de escopo já aceito para o take-home. |
| ASM-015 | Gates de segurança usarão ferramentas gratuitas e executáveis no GitHub Actions, com versões fixadas. | confirmada | Escolha compatível com o objetivo de entrega reproduzível. |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-004 | Podemos confirmar o padrão BFF recomendado para o frontend, em vez de expor tokens a uma SPA? | respondida | Sim, padrão BFF confirmado pelo usuário em 2026-08-16. |
| Q-005 | A evidência de carga pode ser gerada em qualquer ambiente isolado documentado, desde que commit e recursos estejam registrados? | respondida | Sim, ambiente isolado documentado confirmado pelo usuário em 2026-08-16. |
