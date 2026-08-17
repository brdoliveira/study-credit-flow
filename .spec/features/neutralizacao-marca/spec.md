# Spec: Neutralização de marca

> feature: neutralizacao-marca
> status: rascunho

## Contexto

O repositório será público e deve apresentar uma solução autoral de portfólio, sem nomes, marcas ou namespaces da empresa usada como referência inicial.

## Histórias

### US-023 — Publicar uma solução com identidade própria

Como pessoa mantenedora do portfólio, quero remover a identidade da empresa de referência, para publicar o projeto sem sugerir vínculo, autoria ou endosso corporativo.

#### AC-080 — Árvore pública sem referência à marca original

- **Dado** o estado atual do repositório, incluindo código, testes, documentação, specs e nomes de arquivos
- **Quando** a verificação de neutralidade examinar todos os arquivos publicáveis
- **Então** nenhuma grafia da marca original será encontrada em caminho ou conteúdo

#### AC-081 — Namespace autoral e compilável

- **Dado** o perfil público `brdoliveira` e o projeto Credit Flow
- **Quando** o namespace Kotlin e o grupo Gradle forem inspecionados
- **Então** o código usará `io.github.brdoliveira.creditflow`, mantendo aplicação e testes compiláveis

## Fora de escopo

- Reescrever ou apagar branches locais antigas.
- Enviar commits ou branches ao GitHub.
- Manter o PDF original do desafio, pois conteúdo binário não oferece garantia confiável de neutralização.

## Suposições

| ID | Suposição | Status | Resolução |
|---|---|---|---|
| ASM-016 | O namespace pode usar a identidade pública observada no remoto GitHub. | confirmada | O remoto configurado é `github.com/brdoliveira/study-credit-flow`. |

## Perguntas em aberto

| ID | Pergunta | Status | Resposta |
|---|---|---|---|
| Q-006 | A publicação final deve usar uma branch limpa sem os commits locais que continham a marca? | aberta | Aguardando decisão antes de preparar a branch pública. |
