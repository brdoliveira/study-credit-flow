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
variable "tags" {
  type    = map(string)
  default = { Project = "credito-rotativo", ManagedBy = "terraform" }
}
