resource "aws_ecr_repository" "app" {
  name = "${var.project_name}-backend"

  # 같은 태그(latest 등)를 덮어쓸 수 있어야 배포가 단순해진다.
  image_tag_mutability = "MUTABLE"

  # push 시 CVE 스캔 (무료)
  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = {
    Name = "${var.project_name}-backend-ecr"
  }
}

# 프리티어 500MB/월을 넘기지 않도록 오래된 이미지를 자동 정리한다.
# tagStatus="any" 규칙은 반드시 가장 높은 rulePriority여야 한다(ECR 제약).
resource "aws_ecr_lifecycle_policy" "app" {
  repository = aws_ecr_repository.app.name

  policy = jsonencode({
    rules = [
      {
        # 태그 없는(고아) 이미지는 1일 후 삭제
        rulePriority = 1
        description  = "Expire untagged images after 1 day"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },
      {
        # 최근 5개 이미지만 유지 (프리티어 500MB 초과 방지)
        rulePriority = 2
        description  = "Keep only the 5 most recent images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 5
        }
        action = { type = "expire" }
      },
    ]
  })
}

# ---------- GitHub Actions → ECR push (OIDC) ----------
# 액세스키(장기 자격증명)를 GitHub에 저장하지 않고, Actions가 발급한 단기 토큰으로 역할을 위임받는다.
#
# ⚠️ 이미 AWS 계정에 GitHub OIDC 프로바이더가 있다면 이 리소스 생성이 충돌한다.
#    그 경우 아래 리소스를 지우고 기존 것을 data source로 참조하거나,
#    `terraform import aws_iam_openid_connect_provider.github <ARN>` 으로 가져오면 된다.
resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]

  # AWS는 GitHub OIDC에 대해 신뢰된 CA로 검증하므로 이 값은 사실상 형식적이다.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fee",
  ]
}

resource "aws_iam_role" "github_actions" {
  name = "${var.project_name}-github-actions"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = aws_iam_openid_connect_provider.github.arn
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # 이 레포의 dev 브랜치에서 실행된 워크플로우만 역할을 assume 할 수 있다.
        # 와일드카드(:*)를 쓰면 PR·다른 브랜치·타 워크플로우까지 assume이 가능해진다.
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:ref:refs/heads/dev"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_ecr_push" {
  name = "${var.project_name}-github-actions-ecr-push"
  role = aws_iam_role.github_actions.id

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
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = aws_ecr_repository.app.arn
      },
    ]
  })
}

# CD가 SSH 대신 SSM Run Command로 EC2에서 배포 스크립트를 실행하기 위한 권한.
resource "aws_iam_role_policy" "github_actions_ssm_deploy" {
  name = "${var.project_name}-github-actions-ssm-deploy"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        # 대상 인스턴스는 Name 태그로 조회한다(describe는 리소스 단위 제한 불가).
        Effect   = "Allow"
        Action   = "ec2:DescribeInstances"
        Resource = "*"
      },
      {
        # SendCommand는 배포 대상 인스턴스와 AWS-RunShellScript 문서로만 제한한다.
        Effect = "Allow"
        Action = "ssm:SendCommand"
        Resource = [
          aws_instance.muffin.arn,
          "arn:aws:ssm:${data.aws_region.current.name}::document/AWS-RunShellScript",
        ]
      },
      {
        # 명령 상태/출력 조회는 리소스 단위 제한이 어려워 * 를 쓴다.
        Effect   = "Allow"
        Action   = ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"]
        Resource = "*"
      },
    ]
  })
}
