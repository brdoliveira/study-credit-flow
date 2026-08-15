# Spec: Crédito rotativo

> feature: credito-rotativo
> status: pronta

## Contexto

Construir uma solução backend em Kotlin que simule a avaliação e a liberação de crédito rotativo para clientes de cartão, explique cada decisão, gere relatório e demonstre segurança, extensibilidade, resiliência, observabilidade e execução cloud-native. O frontend existe como apoio à demonstração; o núcleo de negócio permanece no backend.

Fontes desta especificação:

- `SPEC_Credito_Rotativo_Itau.md` — levantamento e arquitetura inicial;
- `CE 3 - Sistema de Regras para Crédito Rotativo em Cartão de Crédito.pdf` — enunciado oficial do case;
- descrição da vaga de Engenharia de Sistemas Backend da comunidade de Crédito Imobiliário.

## Histórias

### US-001 — Solicitar uma avaliação de crédito

Como operador autorizado, quero enviar os dados de um cliente para avaliação, para obter uma decisão de crédito rastreável.

#### AC-001 — Avaliação válida é criada

- **Dado** um operador autenticado com permissão de escrita e um pedido válido com uma nova chave de idempotência
- **Quando** ele solicita uma avaliação
- **Então** a API cria uma única avaliação e responde `201 Created`
- **E** informa o endereço do recurso criado no cabeçalho `Location`

#### AC-002 — Dados inválidos são explicados

- **Dado** um operador autenticado que envia nome ou CPF ausente, score fora de `0..1000`, valores negativos, limite total não positivo ou histórico de gastos inválido
- **Quando** ele solicita uma avaliação
- **Então** a API responde `400 Bad Request`
- **E** identifica cada campo inválido no corpo padronizado de erro

#### AC-003 — Reprovação de crédito não é erro técnico

- **Dado** um pedido válido que viola uma regra bloqueante
- **Quando** a avaliação é concluída
- **Então** a API responde com sucesso e registra a decisão `REJECTED`
- **E** o valor aprovado é zero

### US-002 — Avaliar regras de elegibilidade

Como responsável pelo produto de crédito, quero regras determinísticas e explicáveis, para evitar decisões inconsistentes.

#### AC-004 — Todas as regras registradas são executadas

- **Dado** um conjunto versionado de regras ativas
- **Quando** uma avaliação válida é processada
- **Então** cada regra ativa produz código, nome, severidade, status e motivo
- **E** nenhuma regra deixa de ser registrada após a falha de outra regra

#### AC-005 — Score abaixo do mínimo reprova

- **Dado** um cliente com score abaixo do mínimo configurado
- **Quando** as regras são avaliadas
- **Então** a regra `MINIMUM_SCORE` falha e a decisão é `REJECTED`

#### AC-006 — Excesso de atrasos reprova

- **Dado** um cliente com mais atrasos que o máximo configurado
- **Quando** as regras são avaliadas
- **Então** a regra `MAX_LATE_PAYMENTS` falha e a decisão é `REJECTED`

#### AC-007 — Ausência de limite disponível reprova

- **Dado** um cliente sem limite disponível positivo
- **Quando** as regras são avaliadas
- **Então** a regra `AVAILABLE_LIMIT` falha e a decisão é `REJECTED`

#### AC-008 — Comprometimento excessivo reprova

- **Dado** um cliente cuja fatura atual dividida pelo limite total excede o percentual configurado
- **Quando** as regras são avaliadas
- **Então** a regra `LIMIT_COMMITMENT` falha e a decisão é `REJECTED`

#### AC-009 — Tendência elevada gera alerta explicável

- **Dado** um cliente cujo crescimento de gastos entre o primeiro e o último mês excede o percentual configurado
- **Quando** as regras são avaliadas
- **Então** a regra `RECENT_SPENDING_TREND` retorna `WARNING`
- **E** o alerta não reprova o cliente sozinho

#### AC-010 — Nova regra não altera o orquestrador

- **Dado** uma nova implementação do contrato comum de regra registrada na aplicação
- **Quando** uma avaliação é executada
- **Então** a nova regra participa da avaliação sem alteração no código do orquestrador

#### AC-011 — Mesmas entradas geram a mesma decisão

- **Dado** o mesmo pedido, a mesma configuração e a mesma versão de regras
- **Quando** duas avaliações lógicas são executadas
- **Então** regras, decisão e valor calculado são idênticos

### US-003 — Calcular o valor máximo aprovado

Como cliente elegível, quero receber um valor calculado de forma previsível, para entender quanto crédito foi liberado.

#### AC-012 — Cliente elegível recebe valor calculado

- **Dado** um cliente aprovado por todas as regras bloqueantes
- **Quando** o cálculo é executado
- **Então** o valor aprovado é o menor entre o limite percentual ajustado ao risco e o teto configurado
- **E** o valor não excede o limite disponível

#### AC-013 — Cálculo usa precisão monetária

- **Dado** um cálculo cujo resultado possui mais de duas casas decimais
- **Quando** o valor aprovado é produzido
- **Então** o resultado usa duas casas decimais e o arredondamento configurado

#### AC-014 — Cliente reprovado não executa concessão

- **Dado** uma avaliação com ao menos uma regra bloqueante reprovada
- **Quando** o resultado é consolidado
- **Então** o valor aprovado é zero e a calculadora de concessão não produz valor positivo

### US-004 — Explicar e auditar a decisão

Como auditor, quero reconstruir a decisão sem expor dados pessoais, para comprovar os critérios utilizados.

#### AC-015 — Resposta contém rastreabilidade

- **Dado** uma avaliação concluída
- **Quando** o resultado é devolvido
- **Então** ele contém `evaluationId`, CPF mascarado, decisão, valor, versão das regras, regras executadas, data, duração e `correlationId`

#### AC-016 — Avaliação persiste a fotografia da decisão

- **Dado** uma avaliação concluída
- **Quando** a transação é confirmada
- **Então** a decisão, o valor e os resultados das regras permanecem consultáveis com a versão utilizada

#### AC-017 — CPF completo não vaza

- **Dado** qualquer avaliação ou geração de relatório
- **Quando** logs, métricas, eventos, respostas e PDF são produzidos
- **Então** nenhum deles contém o CPF completo

### US-005 — Evitar efeitos duplicados

Como consumidor da API, quero repetir uma chamada com segurança, para recuperar o resultado após falhas de rede sem duplicar avaliações.

#### AC-018 — Chave de idempotência é obrigatória

- **Dado** um pedido sem `Idempotency-Key` ou com chave fora do formato UUID
- **Quando** a avaliação é solicitada
- **Então** a API responde `400 Bad Request` sem criar avaliação

#### AC-019 — Repetição idêntica devolve o resultado original

- **Dado** uma chave já concluída e o mesmo payload canônico
- **Quando** a solicitação é repetida
- **Então** a API responde `200 OK` com o mesmo `evaluationId`
- **E** não cria nova avaliação nem novo evento

#### AC-020 — Reutilização divergente é rejeitada

- **Dado** uma chave já utilizada
- **Quando** ela é enviada com payload canônico diferente
- **Então** a API responde `409 Conflict` sem alterar o resultado original

#### AC-021 — Concorrência não duplica avaliação

- **Dado** duas requisições concorrentes com a mesma chave e o mesmo payload
- **Quando** ambas são processadas
- **Então** existe uma única avaliação persistida e ambas convergem para o mesmo resultado

### US-006 — Consultar avaliações

Como operador autorizado, quero localizar avaliações anteriores, para analisar resultados e preparar relatórios.

#### AC-022 — Listagem é paginada e filtrável

- **Dado** avaliações persistidas
- **Quando** um operador com permissão de leitura consulta por decisão, período, página e ordenação
- **Então** a API retorna `200 OK` com itens, total, página, tamanho e ordenação aplicada

#### AC-023 — Avaliação pode ser consultada por identificador

- **Dado** uma avaliação persistida
- **Quando** um operador autorizado consulta seu `evaluationId`
- **Então** a API retorna `200 OK` com a decisão explicável completa

#### AC-024 — Avaliação inexistente retorna resposta padronizada

- **Dado** um `evaluationId` inexistente
- **Quando** um operador autorizado consulta o recurso
- **Então** a API responde `404 Not Found` com o corpo padronizado de erro

### US-007 — Gerar relatório PDF

Como operador autorizado, quero baixar o relatório das avaliações filtradas, para apresentar aprovados, reprovados e valores liberados.

#### AC-025 — PDF válido é gerado no backend

- **Dado** avaliações que correspondem aos filtros informados
- **Quando** um operador com permissão de relatório solicita o PDF
- **Então** a API responde `200 OK` com `Content-Type: application/pdf`
- **E** usa `Content-Disposition` de anexo com nome de arquivo seguro

#### AC-026 — PDF contém os dados exigidos

- **Dado** avaliações aprovadas e reprovadas
- **Quando** o PDF é gerado
- **Então** ele apresenta filtros, data de geração, totais, taxas e tabela com identificação mascarada, decisão, valor, data e `evaluationId`

#### AC-027 — Relatório vazio continua válido

- **Dado** filtros que não encontram avaliações
- **Quando** o PDF é solicitado
- **Então** a API devolve um PDF válido com totais iguais a zero e sem linhas de avaliação

#### AC-028 — Filtros inválidos são rejeitados

- **Dado** um período invertido, data inválida ou filtro desconhecido
- **Quando** o relatório é solicitado
- **Então** a API responde `400 Bad Request` com a causa identificada

### US-008 — Proteger a aplicação

Como responsável por segurança, quero autenticação e autorização por escopo, para aplicar menor privilégio aos dados de crédito.

#### AC-029 — Token ausente ou inválido é rejeitado

- **Dado** uma chamada sem token JWT válido
- **Quando** ela acessa um endpoint protegido
- **Então** a API responde `401 Unauthorized` sem revelar dados internos

#### AC-030 — Permissão insuficiente é rejeitada

- **Dado** um usuário autenticado sem o escopo exigido
- **Quando** ele acessa avaliação, consulta, relatório ou operação administrativa
- **Então** a API responde `403 Forbidden`

#### AC-031 — Escopos separam as operações

- **Dado** tokens com `credit:write`, `credit:read`, `credit:report` ou `credit:admin`
- **Quando** cada operação é chamada
- **Então** somente o escopo correspondente concede acesso

#### AC-032 — Transporte produtivo exige HTTPS

- **Dado** a aplicação implantada no ambiente produtivo atrás do balanceador
- **Quando** uma chamada HTTP é recebida
- **Então** ela é redirecionada ou recusada em favor de TLS 1.2 ou superior

### US-009 — Publicar o resultado de forma confiável

Como consumidor de eventos, quero receber a conclusão da avaliação com identidade única, para processar efeitos assíncronos com segurança.

#### AC-033 — Avaliação e Outbox são atômicas

- **Dado** uma nova avaliação concluída
- **Quando** a transação é confirmada
- **Então** a avaliação e um evento de Outbox são persistidos juntos
- **E** nenhuma avaliação fica confirmada sem o respectivo evento

#### AC-034 — Evento possui contrato versionado e sem CPF completo

- **Dado** um evento `CreditEvaluationCompleted`
- **Quando** seu payload é serializado
- **Então** ele contém `eventId`, versão, `evaluationId`, decisão, valor, versão das regras, data e `correlationId`
- **E** não contém CPF completo

#### AC-035 — Publicação temporariamente falha e tenta novamente

- **Dado** um evento pendente e uma falha transitória no broker
- **Quando** o publisher processa a Outbox
- **Então** o evento permanece pendente, registra a tentativa e pode ser republicado com backoff limitado

#### AC-036 — Consumidor ignora evento duplicado

- **Dado** um consumidor que já concluiu o processamento de um `eventId`
- **Quando** o mesmo evento é recebido novamente
- **Então** o efeito não é repetido e a entrega é reconhecida

### US-010 — Operar e diagnosticar o serviço

Como equipe de operação, quero sinais técnicos e de negócio, para detectar falhas e degradações rapidamente.

#### AC-037 — Correlação acompanha a requisição

- **Dado** uma chamada com ou sem identificador de correlação
- **Quando** ela é processada
- **Então** resposta, logs, avaliação e evento usam o mesmo `correlationId` válido

#### AC-038 — Métricas essenciais são expostas

- **Dado** avaliações processadas
- **Quando** as métricas são consultadas por um operador autorizado
- **Então** existem contadores de aprovação, reprovação, erro e falha por regra, além de duração e throughput

#### AC-039 — Health checks distinguem vida e prontidão

- **Dado** a aplicação iniciada
- **Quando** liveness e readiness são consultados
- **Então** liveness representa o processo e readiness falha quando uma dependência obrigatória impede o atendimento

#### AC-040 — Erro técnico tem resposta correlacionável

- **Dado** uma falha interna ou indisponibilidade do banco
- **Quando** uma chamada é processada
- **Então** a API responde `500 Internal Server Error` ou `503 Service Unavailable`
- **E** inclui código estável e `correlationId` sem expor stack trace

### US-011 — Demonstrar o fluxo em uma interface web

Como avaliador do processo seletivo, quero executar e consultar o fluxo visualmente, para compreender a solução durante a apresentação.

#### AC-041 — Tela de avaliação apresenta a decisão explicável

- **Dado** um usuário autenticado com permissão de escrita
- **Quando** ele preenche e envia o formulário de avaliação
- **Então** a tela apresenta decisão, valor, regras, motivos e identificador da avaliação

#### AC-042 — Tela de relatório reutiliza os filtros

- **Dado** um usuário autenticado com permissão de leitura e relatório
- **Quando** ele filtra a listagem e solicita o PDF
- **Então** a tela usa os mesmos filtros na consulta e no download

#### AC-043 — Erros são apresentados sem detalhes internos

- **Dado** uma resposta de validação, autenticação, autorização ou falha técnica
- **Quando** o frontend a recebe
- **Então** a tela apresenta uma mensagem compreensível e o identificador de correlação quando disponível

### US-012 — Executar e entregar com qualidade

Como equipe de engenharia, quero uma entrega reproduzível e verificada, para executar localmente e evoluir com segurança.

#### AC-044 — Ambiente local sobe por Docker Compose

- **Dado** Docker disponível e variáveis documentadas
- **Quando** `docker compose up` é executado
- **Então** aplicação, PostgreSQL, provedor de identidade e broker ficam saudáveis sem segredos no código

#### AC-045 — Pipeline bloqueia mudança inválida

- **Dado** uma alteração enviada ao repositório
- **Quando** o pipeline de CI é executado
- **Então** compilação, testes, análise estática e auditoria da especificação precisam passar antes da geração da imagem

#### AC-046 — Carga nominal atende aos objetivos

- **Dado** o ambiente e o cenário de carga documentados
- **Quando** o teste executa ao menos 10.000 avaliações por minuto
- **Então** o p99 permanece abaixo de 1 segundo e a taxa de erros técnicos permanece abaixo do limite documentado

#### AC-047 — Documentação permite reprodução e defesa técnica

- **Dado** uma pessoa sem conhecimento prévio do repositório
- **Quando** ela segue o README
- **Então** consegue executar, testar e demonstrar a solução
- **E** encontra arquitetura, decisões, trade-offs, limitações, evolução cloud e uso de IA

## Fora de escopo

- Integração real com bureau de crédito, core bancário ou processamento de cartão.
- Decisão baseada em machine learning ou modelo estatístico.
- Provedor corporativo real, MFA, federação, SCIM e gestão completa de usuários.
- Frontend produtivo completo ou design system corporativo.
- Provisionamento ou consumo de recursos pagos na AWS durante o take-home.
- Múltiplos microsserviços implantados; a separação futura será documentada.
- Garantia de entrega exatamente uma vez; a solução assume entrega pelo menos uma vez e consumidores idempotentes.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-001 | Os thresholds demonstrativos serão score mínimo `650`, máximo de `2` atrasos, comprometimento máximo de `80%` e tendência de gastos acima de `20%` como alerta. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-002 | O cálculo será `min(limiteDisponível × 70% × fatorDeRisco, R$ 5.000)`, com fatores `0,50` para score 650–699, `0,75` para 700–749 e `1,00` para 750–1000. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-003 | Valores monetários usarão BRL, duas casas decimais e `HALF_EVEN`. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-004 | O histórico conterá exatamente os três meses mais recentes, em ordem do mais antigo para o mais novo. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-005 | O ambiente local usará PostgreSQL em Docker, Flyway e Testcontainers; H2 não será utilizado. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-006 | O provedor de identidade local será Keycloak via Docker Compose e a arquitetura produtiva aceitará OIDC corporativo ou Amazon Cognito. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-007 | O broker local será Kafka compatível via Docker Compose; a arquitetura produtiva comparará MSK e EventBridge. | confirmada | Confirmado pelo usuário em 2026-08-15. |
| ASM-008 | A interface demonstrativa será HTML, Bootstrap e TypeScript, servida como adaptador web sem regra de negócio. | confirmada | Decisão registrada no documento-base e validada na conversa. |
| ASM-009 | O relatório exigido pelo case será materializado como PDF gerado no backend Kotlin. | confirmada | Decisão validada na conversa. |
| ASM-010 | A chave de idempotência será mantida por 24 horas e o hash será calculado sobre JSON canônico. | confirmada | Confirmado pelo usuário em 2026-08-15. |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-001 | Os thresholds, fórmula, arredondamento e janela de gastos descritos em ASM-001 a ASM-004 podem ser adotados como premissas do take-home? | respondida | Sim, pacote confirmado pelo usuário em 2026-08-15. |
| Q-002 | Keycloak local e Kafka local podem ser adotados conforme ASM-006 e ASM-007? | respondida | Sim, pacote confirmado pelo usuário em 2026-08-15. |
| Q-003 | A retenção de 24 horas da chave de idempotência em ASM-010 é adequada para a demonstração? | respondida | Sim, pacote confirmado pelo usuário em 2026-08-15. |
