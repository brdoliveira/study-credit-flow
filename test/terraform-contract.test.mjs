import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const read = (path) => readFileSync(path, 'utf8');
const all = [
  'infrastructure/terraform/main.tf',
  'infrastructure/terraform/modules/network/main.tf',
  'infrastructure/terraform/modules/service/main.tf',
  'infrastructure/terraform/modules/database/main.tf',
  'infrastructure/terraform/modules/messaging/main.tf',
  'infrastructure/terraform/modules/observability/main.tf',
].map(read).join('\n');

test('@spec:AC-076 Terraform representa rede privada, entrada protegida, ECS, Aurora, eventos, segredos e observabilidade', () => {
  for (const marker of ['aws_subnet" "private', 'aws_lb"', 'aws_wafv2_web_acl', 'rate_based_statement', 'aws_ecs_service', 'aws_rds_cluster', 'aws_msk_cluster', 'aws_secretsmanager_secret', 'aws_kms_key', 'aws_cloudwatch', 'aws_appautoscaling']) {
    assert.match(all, new RegExp(marker));
  }
  assert.match(read('infrastructure/terraform/outputs.tf'), /cost_drivers/);
  for (const marker of ['LoadBalancer = var.load_balancer_arn_suffix', 'TargetGroup  = var.target_group_arn_suffix', 'alarm_actions', 'aws_sns_topic', 'aws_cloudwatch_dashboard', 'TargetResponseTime', 'UnHealthyHostCount', 'MemoryUtilization', 'aws_cloudwatch_log_metric_filter']) {
    assert.match(all, new RegExp(marker), `missing operational observability marker: ${marker}`);
  }
  assert.match(all, /aws-otel-collector:v0\.49\.0/);
  assert.match(all, /xray:PutTraceSegments/);
  assert.match(all, /logs:PutLogEvents/);
});

test('@spec:AC-077 Terraform aplica defaults seguros e documenta backup, HA, least privilege e migrations', () => {
  const docs = read('infrastructure/terraform/README.md');
  for (const marker of ['map_public_ip_on_launch = false', 'assign_public_ip = false', 'storage_encrypted', 'encryption_in_transit', 'enabled_cloudwatch_logs_exports', 'backup_retention_period', 'manage_master_user_password']) {
    assert.match(all, new RegExp(marker));
  }
  assert.doesNotMatch(all, /password\s*=\s*"[^$]/i);
  assert.match(docs, /Multi-AZ|zonas distintas/);
  assert.match(docs, /least|somente `GetSecretValue`/i);
  assert.match(docs, /Flyway|migra/i);
  assert.match(docs, /nunca executa `apply`/i);
});
