# Recuperação operacional

## Gate automatizado local

`./scripts/system-tests.sh` cria um projeto Compose descartável e executa, nesta ordem:

1. login OIDC, criação, consumo Kafka, consulta e PDF;
2. jornada real no Chromium e auditoria axe para WCAG A/AA;
3. parada e recuperação de Kafka, PostgreSQL e Keycloak;
4. confirmação de que a outbox criada sem broker chega a `PUBLISHED` após a volta;
5. `pg_dump` restaurado em banco paralelo, com comparação de contagem e hash dos IDs;
6. implantação deliberadamente inválida e retorno à imagem conhecida;
7. rotação da senha PostgreSQL e substituição saudável do container da aplicação.

O drill se recusa a executar fora de um projeto cujo nome comece por `credit-flow-system-`. Backups e diagnósticos ficam em `.context`, não são versionados e o ambiente é removido mesmo quando uma etapa falha.

## Produção AWS

### Backup e restauração

O Aurora mantém 14 dias de backups, proteção contra exclusão e snapshot final. Trimestralmente, restaure um snapshot em cluster isolado, execute Flyway em modo de validação, compare contagens e hashes das tabelas críticas, rode a jornada de leitura e registre RPO/RTO. Nunca valide restauração sobre o cluster de origem.

### Rotação de segredos

Informe `secret_rotation_lambda_arn` e `secret_rotation_days` no Terraform. A Lambda deve implementar as quatro etapas do Secrets Manager (`createSecret`, `setSecret`, `testSecret`, `finishSecret`). Depois da rotação, force novo deployment do ECS para que as tasks leiam a versão `AWSCURRENT`; confirme readiness antes de encerrar as tasks antigas. O pipeline de produção deve rejeitar ambiente sem rotação configurada.

### Rollback

O serviço ECS usa deployment circuit breaker com rollback automático, 100% de capacidade mínima e readiness pelo target group. Mantenha a imagem anterior por digest. Se o circuit breaker não resolver uma regressão funcional, atualize o serviço para a task definition anterior, aguarde `services-stable`, valide criação/consulta e registre a revisão restaurada. Migrações destrutivas exigem estratégia expand/contract, pois rollback de imagem não desfaz schema.

## Evidência obrigatória

Cada drill deve registrar data, commit/task definition, snapshot ou backup usado, duração, contagens comparadas, resultado da jornada e responsável. Uma falha no restore, na rotação ou no rollback bloqueia promoção até correção e repetição bem-sucedida.
