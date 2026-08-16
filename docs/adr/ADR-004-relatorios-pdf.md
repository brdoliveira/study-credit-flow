# ADR-004 — Geração de relatórios PDF no backend

- Status: aceito
- Data: 2026-08-16

## Contexto

O relatório de avaliações precisa ser reproduzível, não depender do navegador e permitir validação automatizada de conteúdo e estrutura.

## Decisão

Usar Apache PDFBox 3.0.8 no backend. A biblioteca gera o documento diretamente em memória, suporta fontes padrão sem recursos externos e permite extrair o texto nos testes. O endpoint retorna o arquivo como anexo e nunca recebe um nome de arquivo informado pelo cliente.

## Consequências

O layout é implementado explicitamente e deve controlar quebra de páginas. Em troca, a aplicação não depende de Chrome headless, templates remotos ou execução de JavaScript para produzir relatórios.
