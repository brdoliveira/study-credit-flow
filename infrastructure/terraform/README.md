# Arquitetura AWS de referência

Esta configuração representa VPC com sub-redes privadas, ALB HTTPS, ECS/Fargate sem IP público, Aurora PostgreSQL Multi-AZ, MSK com TLS/IAM, Secrets Manager/KMS, CloudWatch e autoscaling. É uma referência para `fmt`, `validate` e scanners; o pipeline nunca executa `apply`.

## Segurança e operação

- Banco, brokers e tasks ficam apenas nas sub-redes privadas; somente o ALB recebe tráfego externo por HTTPS.
- O AWS WAF associado ao ALB bloqueia cada IP que ultrapassa 2.000 requisições na janela padrão de cinco minutos.
- Dados, logs, broker e segredos usam KMS; credenciais do Aurora são gerenciadas pelo Secrets Manager, não por variáveis em texto.
- Aurora mantém 14 dias de backup, proteção contra exclusão, snapshot final e duas instâncias em zonas distintas.
- As policies dão somente `GetSecretValue` e `kms:Decrypt` aos recursos nomeados. Permissões de negócio devem ficar em uma task role separada.
- Flyway roda como task ECS de migração antes da atualização do serviço; a pipeline interrompe o deploy se essa task falhar. Nunca se executa migration concorrente em todas as réplicas.
- NAT/VPC endpoints, Aurora, MSK e Fargate são os principais custos. Dimensionamento e retenção devem ser ajustados por ambiente.

```bash
terraform fmt -check -recursive infrastructure/terraform
terraform -chdir=infrastructure/terraform init -backend=false
terraform -chdir=infrastructure/terraform validate
trivy config --exit-code 1 infrastructure/terraform
```
