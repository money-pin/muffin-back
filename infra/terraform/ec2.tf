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
        Resource = [
          # GetParametersByPath는 조회 기준인 부모 경로 ARN에도 권한이 필요하다.
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}",
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/*",
        ]
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

# 앱 DB 계정 bootstrap에만 사용하는 최소 권한. RDS master secret은 애플리케이션
# 컨테이너에 전달하지 않고, SSM Association이 DB 사용자 생성 시에만 조회한다.
resource "aws_iam_role_policy" "ec2_db_bootstrap" {
  name = "${var.project_name}-ec2-db-bootstrap"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "secretsmanager:GetSecretValue"
        Resource = "arn:aws:secretsmanager:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:secret:rds!db-*"
      },
      {
        # RDS 관리형 secret ARN은 master password 관리가 활성화된 뒤 생성되므로,
        # bootstrap 실행 시 DB 인스턴스에서 조회한다.
        Effect   = "Allow"
        Action   = "rds:DescribeDBInstances"
        Resource = "*"
      },
      {
        Effect   = "Allow"
        Action   = "secretsmanager:GetRandomPassword"
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = "ssm:PutParameter"
        Resource = [
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_USERNAME",
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_PASSWORD",
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_APP_BOOTSTRAPPED",
          # 지표 수집 전용 DB 계정 부트스트랩용. 쓰기 권한은 여기 나열한 이름으로만 제한한다.
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_EXPORTER_USERNAME",
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_EXPORTER_PASSWORD",
          "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter/${var.project_name}/${var.environment}/DB_EXPORTER_BOOTSTRAPPED",
        ]
      },
    ]
  })
}

# 온박스 Alloy가 RDS 인스턴스 지표(CPU/FreeableMemory/IOPS/BurstBalance)를 CloudWatch에서
# 직접 긁기 위한 읽기 전용 권한. mysqld-exporter는 MySQL이 들고 있는 상태값만 보고 RDS 안쪽
# 리눅스는 보지 못하므로, 이 지표가 없으면 "쿼리가 느린 게 DB 부하 때문인가"에 답할 수 없다.
#
# Grafana Cloud의 CloudWatch 데이터소스 대신 이 경로를 택한 이유: 그쪽은 만료 없는 액세스 키를
# 외부 서비스에 보관해야 한다. 인스턴스 역할은 자격증명이 AWS 밖으로 나가지 않고 자동 교체된다.
#
# 세 액션 모두 리소스 단위 제한을 지원하지 않아 Resource = "*"다(조회 대상을 미리 특정할 수
# 없는 discovery 계열 API). 대신 읽기 전용이고 지표 데이터 외에는 아무것도 반환하지 않는다.
resource "aws_iam_role_policy" "ec2_cloudwatch_read" {
  name = "${var.project_name}-ec2-cloudwatch-read"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "cloudwatch:GetMetricData",
          "cloudwatch:ListMetrics",
          # exporter가 태그(Project=muffin)로 대상을 찾을 때 쓴다. 인스턴스 식별자를
          # 하드코딩하지 않기 위해 필요하다.
          "tag:GetResources",
        ]
        Resource = "*"
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

  # Windows 체크아웃의 CRLF가 user_data 해시를 바꿔 불필요한 인스턴스 교체를 만들지 않도록 LF로 정규화한다.
  user_data                   = replace(file("${path.module}/user_data.sh"), "\r\n", "\n")
  user_data_replace_on_change = true

  root_block_device {
    # 최신 Amazon Linux 2023 AMI의 루트 스냅샷이 30GB라 그보다 작으면 InvalidBlockDeviceMapping 발생.
    volume_size = 30
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
  #
  # user_data도 같이 무시한다. 이 스크립트는 최초 부팅에만 실행되므로 파일을 고쳐도
  # 살아있는 인스턴스에는 아무 효과가 없고(실제 반영은 박스에서 수동으로 한다),
  # 그런데도 apply 때마다 인스턴스를 통째로 교체해 버린다. 교체 비용은 앱 재배포와
  # HTTPS 인증서 수동 재발급이라 얻는 것에 비해 크다.
  #
  # 의도적으로 박스를 재구축할 때는 명시적으로 지시한다:
  #   terraform apply -replace=aws_instance.muffin
  lifecycle {
    ignore_changes = [ami, user_data]
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
