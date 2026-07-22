resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "EC2 instance security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    # GitHub Actions(CD)가 SSH로 배포하는데 러너 IP가 매번 랜덤이라 특정 IP로 제한할 수 없다.
    # 대신 user_data.sh에서 비밀번호 로그인을 강제로 꺼서(키 기반 인증만 허용) 방어한다.
    description = "SSH (key-only auth enforced in user_data.sh)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    # Nginx를 붙이기 전 임시 테스트용. 리버스 프록시 구성 후에는 이 규칙을 제거하는 것을 권장.
    # (AWS는 description에 ASCII만 허용하므로 한글 설명은 주석으로 둔다)
    description = "App direct access (temporary, remove after Nginx)"
    from_port   = 8080
    to_port     = 8080
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-ec2-sg"
  }
}

resource "aws_security_group" "rds" {
  name = "${var.project_name}-rds-sg"

  # EC2 보안그룹에서 오는 트래픽만 허용한다.
  description = "RDS security group - allows access from EC2 SG only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "MySQL from EC2 only"
    from_port       = 3306
    to_port         = 3306
    protocol        = "tcp"
    security_groups = [aws_security_group.ec2.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-rds-sg"
  }
}
