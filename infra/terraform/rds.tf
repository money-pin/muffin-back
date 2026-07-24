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

  # 메이저 버전 업그레이드(예: 8.0 -> 8.4)를 허용한다. 없으면 db_engine_version을
  # 메이저 상향할 때 apply가 거부된다. 실제 업그레이드는 유지보수 창에 수행됨.
  allow_major_version_upgrade = true

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
  backup_retention_period   = var.db_backup_retention_period
  deletion_protection       = true

  tags = {
    Name = "${var.project_name}-db"
  }
}
