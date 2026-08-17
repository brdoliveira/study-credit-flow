data "aws_availability_zones" "available" { state = "available" }

resource "aws_kms_key" "application" {
  description             = "Encryption key for credit-flow data"
  deletion_window_in_days = 30
  enable_key_rotation     = true
}

resource "aws_secretsmanager_secret" "application" {
  name                    = "${var.name}/runtime"
  kms_key_id              = aws_kms_key.application.arn
  recovery_window_in_days = 30
}

module "network" {
  source = "./modules/network"
  name   = var.name
  cidr   = var.vpc_cidr
  azs    = slice(data.aws_availability_zones.available.names, 0, 2)
}

module "database" {
  source             = "./modules/database"
  name               = var.name
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  service_sg_id      = module.service.service_sg_id
  kms_key_arn        = aws_kms_key.application.arn
}

module "messaging" {
  source             = "./modules/messaging"
  name               = var.name
  vpc_id             = module.network.vpc_id
  private_subnet_ids = module.network.private_subnet_ids
  service_sg_id      = module.service.service_sg_id
  kms_key_arn        = aws_kms_key.application.arn
}

module "service" {
  source             = "./modules/service"
  name               = var.name
  vpc_id             = module.network.vpc_id
  vpc_cidr           = var.vpc_cidr
  public_subnet_ids  = module.network.public_subnet_ids
  private_subnet_ids = module.network.private_subnet_ids
  container_image    = var.container_image
  certificate_arn    = var.certificate_arn
  desired_count      = var.desired_count
  secret_arn         = aws_secretsmanager_secret.application.arn
  kms_key_arn        = aws_kms_key.application.arn
}

module "observability" {
  source                   = "./modules/observability"
  name                     = var.name
  cluster_name             = module.service.cluster_name
  service_name             = module.service.service_name
  load_balancer_arn_suffix = module.service.load_balancer_arn_suffix
  target_group_arn_suffix  = module.service.target_group_arn_suffix
  service_log_group_name   = module.service.log_group_name
  alarm_notification_email = var.alarm_notification_email
}
