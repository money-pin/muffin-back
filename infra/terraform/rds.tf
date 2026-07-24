resource "aws_db_subnet_group" "muffin" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

resource "random_id" "final_snapshot" {
  byte_length = 4
}

resource "aws_db_instance" "muffin" {
  identifier     = "${var.project_name}-db"
  engine         = "mysql"
  engine_version = var.db_engine_version

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"

  # 메이저 버전 업그레이드(예: 8.0 -> 8.4)를 허용한다. 없으면 db_engine_version을
  # 메이저 상향할 때 apply가 거부된다. 실제 업그레이드는 유지보수 창에 수행됨.
  allow_major_version_upgrade = true
  auto_minor_version_upgrade  = true

  # 저장 시 암호화(KMS 기본 aws/rds 키). 투자/자산 데이터 보호용.
  # 생성 후에는 변경 불가하므로 반드시 최초 생성 시점에 켠다.
  storage_encrypted = true

  db_name                     = var.db_name
  username                    = var.db_username
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.muffin.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az                  = false
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final-snapshot-${random_id.final_snapshot.hex}"
  backup_retention_period   = var.db_backup_retention_period
  deletion_protection       = true

  tags = {
    Name = "${var.project_name}-db"
  }
}

# RDS master 계정은 애플리케이션에 전달하지 않는다. RDS가 관리하는 master secret을
# 사용해 SSM State Manager가 최초 1회 schema 범위의 애플리케이션 계정을 만든다.
resource "aws_ssm_association" "db_app_user_bootstrap" {
  name                             = "AWS-RunShellScript"
  wait_for_success_timeout_seconds = 900

  targets {
    key    = "InstanceIds"
    values = [aws_instance.muffin.id]
  }

  parameters = {
    commands = templatefile("${path.module}/db_app_user_bootstrap.sh.tftpl", {
      aws_region      = var.aws_region
      db_identifier   = aws_db_instance.muffin.identifier
      db_endpoint     = aws_db_instance.muffin.address
      db_name         = var.db_name
      app_db_username = var.app_db_username
      ssm_path        = "/${var.project_name}/${var.environment}"
    })
  }

  depends_on = [
    aws_iam_role_policy.ec2_db_bootstrap,
    aws_iam_role_policy_attachment.ec2_ssm_core,
  ]
}
