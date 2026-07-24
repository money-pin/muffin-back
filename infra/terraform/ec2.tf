data "aws_ami" "amazon_linux_2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "muffin" {
  key_name = "${var.project_name}-ec2-key"

  # file()은 ~ 를 확장하지 않으므로 pathexpand()로 홈 디렉터리를 먼저 풀어준다.
  public_key = join(" ", slice(split(" ", trimspace(file(pathexpand(var.ssh_public_key_path)))), 0, 2))
}

# EC2가 ECR/SSM에 접근할 수 있도록 하는 역할 (액세스키를 코드/서버에 박아두지 않기 위함)
resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "ec2.amazonaws.com"
      }
    }]
  })
}

# EC2가 ECR에서 이미지를 pull 할 수 있도록 하는 권한.
# 서버에 레지스트리 비밀번호를 저장할 필요가 없다(IAM Role로 자동 인증).
resource "aws_iam_role_policy" "ec2_ecr_pull" {
  name = "${var.project_name}-ec2-ecr-pull"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 토큰 발급은 리소스 단위 제한이 불가해 * 를 써야 한다.
        Effect   = "Allow"
        Action   = "ecr:GetAuthorizationToken"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = aws_ecr_repository.app.arn
      },
    ]
  })
}

data "aws_caller_identity" "current" {}

data "aws_region" "current" {}

# 앱 시크릿(DB 비밀번호, JWT, API 키 등)을 SSM Parameter Store에서 읽기 위한 권한.
# 서버에 .env 평문 파일을 두지 않고, 값 교체도 SSH 없이 put-parameter로 가능하다.
resource "aws_iam_role_policy" "ec2_ssm_read" {
  name = "${var.project_name}-ec2-ssm-read"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath",
        ]
        Resource = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/*"
      },
      {
        # SecureString 복호화. SSM을 경유한 호출로만 제한한다.
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
        Condition = {
          StringEquals = {
            "kms:ViaService" = "ssm.${data.aws_region.current.name}.amazonaws.com"
          }
        }
      },
    ]
  })
}

# SSM Run Command/Session Manager로 이 인스턴스를 관리하기 위한 표준 권한(ssmmessages/ec2messages 등).
# CD가 SSH 대신 SSM으로 배포 스크립트를 실행하려면 인스턴스가 SSM에 등록돼 있어야 한다.
resource "aws_iam_role_policy_attachment" "ec2_ssm_core" {
  role       = aws_iam_role.ec2.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-ec2-instance-profile"
  role = aws_iam_role.ec2.name
}

resource "aws_instance" "muffin" {
  # ec2_ami_id가 지정되면 그 값을, 아니면 최신 AL2023을 사용.
  ami                    = var.ec2_ami_id != "" ? var.ec2_ami_id : data.aws_ami.amazon_linux_2023.id
  instance_type          = var.ec2_instance_type
  key_name               = aws_key_pair.muffin.key_name
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.ec2.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2.name

  user_data                   = file("${path.module}/user_data.sh")
  user_data_replace_on_change = true

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted   = true
  }

  # IMDSv2(토큰) 강제. 토큰 없이 접근 가능한 IMDSv1을 막아, SSRF 등으로 인스턴스
  # IAM 자격증명이 유출되는 경로를 차단한다.
  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  # most_recent AMI가 나중에 갱신돼도 인스턴스를 교체하지 않도록 ami 변경을 무시한다.
  # (의도적으로 AMI를 바꾸려면 var.ec2_ami_id로 명시 후 이 항목을 잠시 조정)
  lifecycle {
    ignore_changes = [ami]
  }

  tags = {
    Name = "${var.project_name}-backend"
  }
}

# 고정 퍼블릭 IP. 인스턴스가 교체(user_data 변경 등)되거나 stop/start 해도 IP가 유지된다.
resource "aws_eip" "muffin" {
  domain   = "vpc"
  instance = aws_instance.muffin.id

  tags = {
    Name = "${var.project_name}-eip"
  }

  # 퍼블릭 서브넷의 인터넷 게이트웨이가 준비된 뒤 연결되도록 보장
  depends_on = [aws_internet_gateway.main]
}
