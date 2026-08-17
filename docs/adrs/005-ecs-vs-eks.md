# ADR-005 — ECS/Fargate antes de EKS

- Status: proposto para produção
- Data: 2026-08-16

## Decisão

Executar a imagem OCI inicialmente em ECS/Fargate, distribuída em múltiplas AZs e atrás de ALB/API Gateway. Avaliar EKS apenas quando uma plataforma Kubernetes existente ou requisitos de extensibilidade justificarem seu custo.

## Trade-offs

ECS reduz operação de cluster, patching e componentes auxiliares. EKS oferece ecossistema Kubernetes e portabilidade maiores, com mais complexidade, custo e superfície operacional.
