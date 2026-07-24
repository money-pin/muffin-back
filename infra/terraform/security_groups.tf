resource "aws_security_group" "ec2" {
  name        = "${var.project_name}-ec2-sg"
  description = "EC2 instance security group"
  vpc_id      = aws_vpc.main.id

  ingress {
    # 허용 대역은 var.ssh_allowed_cidrs로 조정한다. 기본값이 전체 허용인 이유는
    # GitHub Actions(CD) 러너 IP가 매번 랜덤이기 때문이며, 이 경우 user_data.sh에서
    # 비밀번호 로그인을 꺼(키 기반 인증만 허용) 방어한다.
    # 고정 IP 러너/VPN/배스천 도입 또는 SSM Session Manager 전환 시 반드시 좁힐 것.
    description = "SSH (key-only auth enforced in user_data.sh)"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = var.ssh_allowed_cidrs
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
