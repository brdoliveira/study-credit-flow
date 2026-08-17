# Spec: Renomear infraestrutura compartilhada para platform

> feature: renomear-infraestrutura-platform
> status: pronta

## Contexto

O projeto possui `evaluation.infrastructure` para os adaptadores da feature e `creditflow.infrastructure` para capacidades transversais. Embora correto, o nome repetido dificulta distinguir responsabilidades. A infraestrutura compartilhada passa a se chamar `platform`, preservando a infraestrutura interna da feature.

## Histórias

### US-030 — Distinguir a plataforma compartilhada dos adaptadores da feature

Como pessoa desenvolvedora, quero encontrar capacidades transversais em `platform`, para que a árvore de pacotes deixe explícito o que pertence ao sistema inteiro e o que pertence à avaliação de crédito.

#### AC-096 — Plataforma compartilhada possui namespace próprio

- **Dado** o monólito modular organizado pela feature `evaluation`
- **Quando** os pacotes de produção, testes e a documentação arquitetural são inspecionados
- **Então** capacidades transversais usam `creditflow.platform`, não existe o pacote global `creditflow.infrastructure`, `evaluation.infrastructure` permanece inalterado e todos os contratos automatizados continuam passando

## Fora de escopo

- Renomear `evaluation.infrastructure` ou mudar suas responsabilidades.
- Alterar endpoints, regras de crédito, persistência, mensageria ou comportamento externo.
- Separar a plataforma compartilhada em outro módulo Gradle ou deploy.

## Suposições

Nenhuma. A nomenclatura `platform` foi escolhida explicitamente pelo usuário.

## Perguntas em aberto

Nenhuma.
