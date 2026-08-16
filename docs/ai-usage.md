# Uso de IA no desenvolvimento

IA generativa foi usada como assistente para estruturar a especificação, decompor tarefas, produzir partes da implementação e sugerir testes e documentação.

## Controles aplicados

- requisitos, fórmula e premissas foram confirmados pelo responsável humano;
- cada critério de aceite recebeu teste anotado `@spec:AC-xxx`;
- código gerado foi compilado, testado e auditado mecanicamente;
- versões de bibliotecas foram verificadas em documentação oficial;
- nenhum segredo, token real ou dado pessoal de cliente foi fornecido à IA;
- resultados de performance não foram inventados: o repositório contém o cenário e os thresholds, não uma alegação de capacidade sem ensaio.

## Pontos que exigem revisão humana

Antes de produção: threat modeling, LGPD/DPIA, política de retenção, validação das regras com Risco/Compliance, configuração do IdP corporativo, gestão de chaves/segredos, sizing, teste de carga, pentest, backup/restore e plano de continuidade.

O histórico Git e os artefatos `.spec/` preservam a rastreabilidade das decisões e das verificações executadas.
