# Spec: Arquitetura Kotlin

> feature: arquitetura-kotlin
> status: pronta

## Contexto

O código Kotlin concentra tipos públicos diferentes no mesmo arquivo, mistura KDocs em inglês e português e mantém controllers e serviços no mesmo pacote web. A refatoração deve tornar a navegação previsível, alinhar as dependências à arquitetura documentada e preservar todos os contratos funcionais existentes.

## Histórias

### US-024 — Navegar pelo código com estrutura previsível

Como pessoa desenvolvedora, quero encontrar cada responsabilidade em arquivo e pacote próprios, para compreender e alterar o sistema sem depender de busca textual extensa.

#### AC-082 — Um tipo público principal por arquivo

- **Dado** o código Kotlin de produção
- **Quando** sua organização estrutural for verificada
- **Então** cada arquivo terá no máximo um tipo público ou interno de nível superior com o mesmo nome do arquivo, permitindo apenas auxiliares privados coesos

#### AC-083 — KDocs consistentes em português

- **Dado** todo tipo e membro público ou interno do código Kotlin de produção
- **Quando** sua documentação for analisada
- **Então** haverá KDoc em português explicando responsabilidade, parâmetros ou decisões relevantes e não existirão comentários documentais em inglês

#### AC-084 — Adaptador web separado por responsabilidade

- **Dado** o módulo de avaliação de crédito
- **Quando** seus pacotes forem inspecionados
- **Então** controllers, DTOs, mapeadores e tratamento de erros estarão separados, sem classes de serviço de aplicação no pacote de controllers

### US-025 — Manter fronteiras arquiteturais simples

Como pessoa responsável pela arquitetura, quero que a aplicação use diretamente o domínio e dependa de portas apenas para recursos externos, para evitar modelos e traduções sem benefício.

#### AC-085 — Aplicação depende diretamente do domínio

- **Dado** o fluxo de avaliação
- **Quando** suas dependências forem inspecionadas
- **Então** o caso de uso utilizará o motor, o cálculo e os modelos do domínio diretamente, sem portas internas que simulem essas dependências

#### AC-086 — Modelo de avaliação sem duplicações conceituais

- **Dado** os modelos de decisão, severidade, estado de regra e avaliação persistida
- **Quando** as declarações Kotlin forem comparadas
- **Então** existirá uma única representação tipada de cada conceito compartilhada por domínio, aplicação e portas, com serialização restrita aos adaptadores

#### AC-087 — Casos de uso fora do adaptador web

- **Dado** as jornadas de criar, consultar, listar e gerar relatório
- **Quando** seus pontos de entrada forem analisados
- **Então** os controllers dependerão de casos de uso da aplicação e não acessarão repositórios, serializadores ou serviços de infraestrutura diretamente

### US-026 — Preservar o comportamento entregue

Como pessoa avaliadora do projeto, quero que a reorganização interna preserve os contratos existentes, para continuar executando a mesma demonstração e as mesmas integrações.

#### AC-088 — Contratos funcionais preservados

- **Dado** o conjunto de testes unitários, HTTP, segurança, persistência, mensageria, PDF e observabilidade existente
- **Quando** a refatoração for concluída
- **Então** as suítes continuarão compilando e passando sem enfraquecimento ou remoção de cenários

#### AC-089 — Arquitetura documentada corresponde ao código

- **Dado** a documentação pública de arquitetura
- **Quando** ela for comparada à árvore de pacotes implementada
- **Então** apresentará a organização por feature, as portas externas e o fluxo entre controllers, casos de uso, domínio e adaptadores

## Fora de escopo

- Alterar endpoints, payloads HTTP, schema OpenAPI ou códigos de status.
- Alterar regras, cálculos, schema PostgreSQL, eventos, autenticação ou conteúdo do PDF.
- Dividir o deploy em microsserviços ou módulos Gradle independentes.
- Traduzir nomes de classes, métodos, campos, mensagens de erro ou contratos para português.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-017 | KDoc em tudo significa documentar tipos e membros públicos ou internos; auxiliares privados triviais não receberão comentários que apenas repitam o código. | confirmada | Interpretação pragmática do pedido: cobertura consistente sem documentação redundante. |
| ASM-018 | A refatoração deve preservar integralmente os contratos observáveis atuais. | confirmada | O pedido exige validar que nada quebrou e que os testes continuam passando. |

## Perguntas em aberto

Nenhuma.
