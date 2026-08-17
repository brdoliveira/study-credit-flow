variable "name" { type = string }
variable "cluster_name" { type = string }
variable "service_name" { type = string }
variable "load_balancer_arn_suffix" { type = string }
variable "target_group_arn_suffix" { type = string }
variable "service_log_group_name" { type = string }
variable "alarm_notification_email" { type = string }

data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

resource "aws_kms_key" "operational_alerts" {
  description             = "Encrypts ${var.name} operational alert notifications"
  enable_key_rotation     = true
  deletion_window_in_days = 30
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "AccountAdministration"
        Effect    = "Allow"
        Principal = { AWS = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:root" }
        Action    = "kms:*"
        Resource  = "*"
      },
      {
        Sid       = "CloudWatchAndSnsUse"
        Effect    = "Allow"
        Principal = { Service = ["cloudwatch.amazonaws.com", "sns.amazonaws.com"] }
        Action    = ["kms:Decrypt", "kms:GenerateDataKey*"]
        Resource  = "*"
        Condition = {
          StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        }
      }
    ]
  })
}

resource "aws_kms_alias" "operational_alerts" {
  name          = "alias/${var.name}-operational-alerts"
  target_key_id = aws_kms_key.operational_alerts.key_id
}

resource "aws_sns_topic" "operational_alerts" {
  name              = "${var.name}-operational-alerts"
  kms_master_key_id = aws_kms_key.operational_alerts.arn
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.alarm_notification_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.operational_alerts.arn
  protocol  = "email"
  endpoint  = var.alarm_notification_email
}

resource "aws_appautoscaling_target" "service" {
  max_capacity       = 10
  min_capacity       = 2
  resource_id        = "service/${var.cluster_name}/${var.service_name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name}-cpu"
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.service.resource_id
  scalable_dimension = aws_appautoscaling_target.service.scalable_dimension
  service_namespace  = aws_appautoscaling_target.service.service_namespace
  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "errors" {
  alarm_name          = "${var.name}-target-5xx"
  alarm_description   = "Target 5xx responses exceeded the operational threshold."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Sum"
  threshold           = 5
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
  dimensions = {
    LoadBalancer = var.load_balancer_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "latency" {
  alarm_name          = "${var.name}-target-p99-latency"
  alarm_description   = "ALB target p99 latency exceeded the one-second SLO."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  extended_statistic  = "p99"
  threshold           = 1
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
  dimensions = {
    LoadBalancer = var.load_balancer_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${var.name}-unhealthy-targets"
  alarm_description   = "One or more ECS targets failed the readiness probe."
  comparison_operator = "GreaterThanOrEqualToThreshold"
  evaluation_periods  = 2
  metric_name         = "UnHealthyHostCount"
  namespace           = "AWS/ApplicationELB"
  period              = 60
  statistic           = "Maximum"
  threshold           = 1
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
  dimensions = {
    LoadBalancer = var.load_balancer_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu" {
  alarm_name          = "${var.name}-ecs-high-cpu"
  alarm_description   = "ECS service CPU remained above 80 percent."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  metric_name         = "CPUUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 80
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = var.service_name
  }
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory" {
  alarm_name          = "${var.name}-ecs-high-memory"
  alarm_description   = "ECS service memory remained above 85 percent."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 5
  metric_name         = "MemoryUtilization"
  namespace           = "AWS/ECS"
  period              = 60
  statistic           = "Average"
  threshold           = 85
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
  dimensions = {
    ClusterName = var.cluster_name
    ServiceName = var.service_name
  }
}

resource "aws_cloudwatch_log_metric_filter" "application_errors" {
  name           = "${var.name}-structured-errors"
  log_group_name = var.service_log_group_name
  pattern        = "{ $.level = \"ERROR\" }"

  metric_transformation {
    name          = "StructuredApplicationErrors"
    namespace     = "CreditFlow"
    value         = "1"
    default_value = "0"
  }
}

resource "aws_cloudwatch_metric_alarm" "application_errors" {
  alarm_name          = "${var.name}-application-errors"
  alarm_description   = "Structured ERROR logs include HTTP, Kafka, and outbox failures."
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = aws_cloudwatch_log_metric_filter.application_errors.metric_transformation[0].name
  namespace           = aws_cloudwatch_log_metric_filter.application_errors.metric_transformation[0].namespace
  period              = 60
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.operational_alerts.arn]
  ok_actions          = [aws_sns_topic.operational_alerts.arn]
}

resource "aws_cloudwatch_dashboard" "service" {
  dashboard_name = "${var.name}-operation"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        x    = 0, y = 0, width = 12, height = 6
        properties = {
          title  = "ALB errors and unhealthy targets"
          region = data.aws_region.current.name
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", var.load_balancer_arn_suffix, "TargetGroup", var.target_group_arn_suffix],
            [".", "UnHealthyHostCount", ".", ".", ".", "."]
          ]
        }
      },
      {
        type = "metric"
        x    = 12, y = 0, width = 12, height = 6
        properties = {
          title  = "ALB target latency"
          region = data.aws_region.current.name
          stat   = "p99"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.load_balancer_arn_suffix, "TargetGroup", var.target_group_arn_suffix]
          ]
        }
      },
      {
        type = "metric"
        x    = 0, y = 6, width = 12, height = 6
        properties = {
          title  = "ECS CPU and memory"
          region = data.aws_region.current.name
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.cluster_name, "ServiceName", var.service_name],
            [".", "MemoryUtilization", ".", ".", ".", "."]
          ]
        }
      },
      {
        type = "log"
        x    = 12, y = 6, width = 12, height = 6
        properties = {
          title  = "Recent structured errors"
          region = data.aws_region.current.name
          query  = "SOURCE '${var.service_log_group_name}' | fields @timestamp, correlationId, message | filter level = 'ERROR' | sort @timestamp desc | limit 50"
          view   = "table"
        }
      }
    ]
  })
}

output "alerts_topic_arn" { value = aws_sns_topic.operational_alerts.arn }
output "dashboard_name" { value = aws_cloudwatch_dashboard.service.dashboard_name }
