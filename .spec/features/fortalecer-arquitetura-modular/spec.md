# Spec: Fortalecer arquitetura modular

> feature: fortalecer-arquitetura-modular
> status: pronta

## Contexto

A separação atual entre domínio, aplicação e infraestrutura funciona, mas parte das fronteiras é verificada por buscas textuais incompletas, alguns componentes específicos de avaliação aparecem como infraestrutura global, filtros aceitam estados inválidos e o contrato do evento é duplicado entre Kotlin e SQL. Esta refatoração torna essas fronteiras explícitas e mecanicamente verificáveis sem alterar o comportamento externo da aplicação.

## Histórias

### US-027 — Evoluir o serviço sem romper as camadas

Como pessoa desenvolvedora, quero que dependências proibidas sejam detectadas automaticamente, para que mudanças futuras não acoplem domínio ou aplicação à infraestrutura.

#### AC-090 — Fronteiras de dependência são verificadas integralmente

- **Dado** o código Kotlin organizado em domínio, aplicação, infraestrutura compartilhada e adaptadores
- **Quando** os testes estruturais são executados
- **Então** qualquer importação do domínio para camadas externas ou da aplicação para infraestrutura faz o teste falhar, e o domínio real da feature não pode ser ignorado por um caminho vazio

#### AC-091 — Testes acompanham os pacotes de produção

- **Dado** os testes unitários do domínio e do caso de uso de avaliação
- **Quando** a estrutura de pacotes é inspecionada
- **Então** seus caminhos e declarações de pacote espelham `evaluation/domain` e `evaluation/application`

### US-028 — Encontrar cada responsabilidade no módulo que a possui

Como pessoa mantenedora, quero que componentes específicos de avaliação estejam dentro do módulo `evaluation`, para que a raiz do projeto não misture código de negócio com infraestrutura realmente compartilhada.

#### AC-092 — Componentes específicos pertencem ao módulo de avaliação

- **Dado** eventos, mensageria, outbox, métricas e idempotência específicos da avaliação de crédito
- **Quando** a árvore de pacotes é verificada
- **Então** esses componentes ficam abaixo de `evaluation`, enquanto a composição Spring e os recursos realmente compartilhados permanecem fora do módulo

#### AC-093 — Filtro de decisão aceita somente estados do domínio

- **Dado** uma consulta de avaliações por decisão
- **Quando** o adaptador web converte o parâmetro recebido para a aplicação
- **Então** o contrato interno usa `CreditDecisionStatus` em vez de texto livre e preserva os filtros HTTP já aceitos

#### AC-094 — Evento da outbox possui uma única fonte de contrato

- **Dado** uma avaliação persistida com sucesso
- **Quando** sua entrada de outbox é criada na mesma transação
- **Então** o payload é serializado a partir de `CreditEvaluationCompleted`, sem uma segunda definição manual dos campos do evento em trigger SQL

### US-029 — Preservar o comportamento durante a refatoração

Como pessoa avaliadora do projeto, quero documentação e testes coerentes com a implementação, para que a arquitetura possa ser comprovada e defendida.

#### AC-095 — Documentação e contratos continuam coerentes

- **Dado** a nova organização dos pacotes e a criação explícita da outbox
- **Quando** a documentação e as suítes automatizadas são verificadas
- **Então** a árvore documentada corresponde ao código e os contratos funcionais existentes continuam passando

## Fora de escopo

- Alterar endpoints, payloads HTTP, regras de crédito ou configuração de segurança.
- Separar o monólito em múltiplos deploys.
- Trocar PostgreSQL, Kafka, Spring Boot ou a biblioteca de PDF.
- Criar uma nova versão pública do evento `CreditEvaluationCompleted`.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-019 | A refatoração deve preservar todos os contratos observáveis atuais; apenas namespaces internos, verificações estruturais e a autoria técnica da outbox serão alterados. | confirmada | O usuário pediu a implementação dos cinco pontos de melhoria identificados na auditoria, que foram propostos como refatorações sem mudança funcional. |

## Perguntas em aberto

Nenhuma.
