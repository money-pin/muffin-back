resource "aws_db_subnet_group" "muffin" {
  name       = "${var.project_name}-db-subnet-group"
  subnet_ids = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-db-subnet-group"
  }
}

resource "aws_db_instance" "muffin" {
  identifier     = "${var.project_name}-db"
  engine         = "mysql"
  engine_version = var.db_engine_version

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"

  # 저장 시 암호화(KMS 기본 aws/rds 키). 투자/자산 데이터 보호용.
  # 생성 후에는 변경 불가하므로 반드시 최초 생성 시점에 켠다.
  storage_encrypted = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.muffin.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false

  multi_az                  = false
  skip_final_snapshot       = false
  final_snapshot_identifier = "${var.project_name}-db-final-snapshot"
  # AWS 무료 플랜은 백업 보관 기간에 상한이 있어 7일을 쓸 수 없다(FreeTierRestrictionError).
  # 유료 플랜으로 전환하면 7일 이상으로 올리는 것을 권장한다(마이그레이션 사고 시 복구 지점).
  backup_retention_period = 1
  deletion_protection     = true

  tags = {
    Name = "${var.project_name}-db"
  }
}
