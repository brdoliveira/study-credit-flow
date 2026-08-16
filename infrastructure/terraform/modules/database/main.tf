variable "name" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "service_sg_id" { type = string }
variable "kms_key_arn" { type = string }

resource "aws_security_group" "database" {
  name_prefix = "${var.name}-database-"
  vpc_id      = var.vpc_id
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [var.service_sg_id]
  }
}
resource "aws_db_subnet_group" "this" {
  name       = var.name
  subnet_ids = var.private_subnet_ids
}
resource "aws_rds_cluster" "this" {
  cluster_identifier              = var.name
  engine                          = "aurora-postgresql"
  engine_mode                     = "provisioned"
  database_name                   = "credit_rotativo"
  master_username                 = "credit_admin"
  manage_master_user_password     = true
  db_subnet_group_name            = aws_db_subnet_group.this.name
  vpc_security_group_ids          = [aws_security_group.database.id]
  storage_encrypted               = true
  kms_key_id                      = var.kms_key_arn
  backup_retention_period         = 14
  preferred_backup_window         = "03:00-04:00"
  deletion_protection             = true
  enabled_cloudwatch_logs_exports = ["postgresql"]
  skip_final_snapshot             = false
}
resource "aws_rds_cluster_instance" "this" {
  count              = 2
  identifier         = "${var.name}-${count.index + 1}"
  cluster_identifier = aws_rds_cluster.this.id
  instance_class     = "db.r6g.large"
  engine             = aws_rds_cluster.this.engine
}
output "endpoint" { value = aws_rds_cluster.this.endpoint }
