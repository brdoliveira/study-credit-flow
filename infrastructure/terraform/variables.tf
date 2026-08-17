variable "aws_region" {
  type    = string
  default = "sa-east-1"
}
variable "name" {
  type    = string
  default = "credito-rotativo"
}
variable "vpc_cidr" {
  type    = string
  default = "10.42.0.0/16"
}
variable "container_image" { type = string }
variable "certificate_arn" { type = string }
variable "desired_count" {
  type    = number
  default = 2
}
variable "alarm_notification_email" {
  description = "Optional email subscribed to operational CloudWatch alarms."
  type        = string
  default     = ""
}
variable "secret_rotation_lambda_arn" {
  description = "Optional Secrets Manager rotation Lambda ARN; required by the production deployment process."
  type        = string
  default     = ""
}
variable "secret_rotation_days" {
  description = "Maximum age of the runtime application secret."
  type        = number
  default     = 30
  validation {
    condition     = var.secret_rotation_days >= 1 && var.secret_rotation_days <= 365
    error_message = "secret_rotation_days must be between 1 and 365."
  }
}
variable "tags" {
  type    = map(string)
  default = { Project = "credito-rotativo", ManagedBy = "terraform" }
}
