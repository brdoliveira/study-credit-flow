# ADR-004 — Apache PDFBox para relatórios

- Status: aceito
- Data: 2026-08-16

## Decisão

Gerar PDF no backend com Apache PDFBox 3.0.8, em memória, usando nome de anexo controlado pela aplicação. O teste abre o documento e extrai seu texto.

## Trade-offs

Não dependemos de navegador headless ou serviço externo. Em contrapartida, quebra de página e layout são explícitos. Para relatórios muito grandes, a geração deverá ser assíncrona e por streaming/objeto em S3.
