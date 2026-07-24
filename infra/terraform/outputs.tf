output "ec2_public_ip" {
  description = "EC2 고정 퍼블릭 IP (EIP)"
  value       = aws_eip.muffin.public_ip
}

output "ssh_command" {
  description = "SSH 접속 명령어 (개인키 경로는 var.ssh_private_key_path)"
  value       = "ssh -i ${var.ssh_private_key_path} ec2-user@${aws_eip.muffin.public_ip}"
}

output "rds_endpoint" {
  description = "RDS 엔드포인트 (application-prod.yml의 DB_URL에 사용)"
  value       = aws_db_instance.muffin.address
}

output "rds_port" {
  value = aws_db_instance.muffin.port
}

output "ecr_repository_name" {
  description = "ECR 리포지토리 이름. GitHub Secrets의 ECR_REPOSITORY에 이 값을 넣는다(URL 아님)."
  value       = aws_ecr_repository.app.name
}

output "ecr_repository_url" {
  description = "ECR 리포지토리 전체 URL (docker pull 등 수동 확인용)"
  value       = aws_ecr_repository.app.repository_url
}

output "github_actions_role_arn" {
  description = "GitHub Actions가 assume 할 IAM 역할 ARN (GitHub Secret AWS_ROLE_ARN에 사용)"
  value       = aws_iam_role.github_actions.arn
}
