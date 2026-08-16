variable "name" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "service_sg_id" { type = string }
variable "kms_key_arn" { type = string }

resource "aws_security_group" "broker" {
  name_prefix = "${var.name}-broker-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 9098
    to_port         = 9098
    protocol        = "tcp"
    security_groups = [var.service_sg_id]
  }
}
resource "aws_msk_cluster" "this" {
  cluster_name           = var.name
  kafka_version          = "3.7.x"
  number_of_broker_nodes = 2
  broker_node_group_info {
    instance_type   = "kafka.m7g.large"
    client_subnets  = var.private_subnet_ids
    security_groups = [aws_security_group.broker.id]
    storage_info {
      ebs_storage_info { volume_size = 100 }
    }
  }
  client_authentication {
    sasl { iam = true }
  }
  encryption_info {
    encryption_at_rest_kms_key_arn = var.kms_key_arn
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }
  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = "/aws/msk/${var.name}"
      }
    }
  }
}
output "bootstrap_brokers" { value = aws_msk_cluster.this.bootstrap_brokers_sasl_iam }
