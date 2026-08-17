output "load_balancer_dns" { value = module.service.load_balancer_dns }
output "database_endpoint" {
  value     = module.database.endpoint
  sensitive = true
}
output "bootstrap_brokers" {
  value     = module.messaging.bootstrap_brokers
  sensitive = true
}
output "runtime_secret_arn" { value = aws_secretsmanager_secret.application.arn }
output "operational_alerts_topic_arn" { value = module.observability.alerts_topic_arn }
output "operational_dashboard_name" { value = module.observability.dashboard_name }
output "cost_drivers" { value = ["NAT gateways", "Aurora instances", "MSK brokers", "Fargate tasks"] }
