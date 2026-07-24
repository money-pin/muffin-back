variable "aws_region" {
  description = "리소스를 생성할 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project_name" {
  description = "리소스 이름에 붙는 프로젝트 접두사"
  type        = string
  default     = "muffin"
}

variable "environment" {
  description = "환경 구분 태그 (prod / dev 등)"
  type        = string
  default     = "prod"
}

# ---------- 네트워크 ----------

variable "vpc_cidr" {
  description = "VPC CIDR 대역"
  type        = string
  default     = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  description = "퍼블릭 서브넷 CIDR 목록 (RDS 서브넷 그룹은 최소 2개 AZ 필요)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]

  validation {
    condition     = length(var.public_subnet_cidrs) >= 2
    error_message = "RDS 서브넷 그룹은 서로 다른 AZ의 서브넷이 최소 2개 필요하므로 CIDR을 2개 이상 지정해야 합니다."
  }
}

# ---------- EC2 ----------

variable "ec2_instance_type" {
  # 프리티어(첫 12개월 t3.micro 750h/월) 유지를 위해 기본값은 t3.micro.
  # 1GB RAM이라 JVM OOM 위험이 있어 user_data.sh에서 swap 2GB를 잡고, 컨테이너 힙을
  # -Xmx512m 수준으로 제한해 운용한다. 트래픽이 커지면 t3.small(비프리티어, ~$19/월)로 상향.
  description = "EC2 인스턴스 타입 (기본 t3.micro=프리티어, 여유가 필요하면 t3.small)"
  type        = string
  default     = "t3.micro"
}

variable "ec2_ami_id" {
  description = "고정할 EC2 AMI ID. 비워두면 최신 Amazon Linux 2023을 자동 선택한다. 재생성 사고를 막으려면 최초 apply 후 실제 AMI ID를 tfvars에 박아두는 것을 권장."
  type        = string
  default     = ""
}

variable "ssh_public_key_path" {
  description = "로컬에 있는 SSH 공개키 경로. `ssh-keygen -t ed25519 -f ~/.ssh/muffin-ec2`로 미리 생성해두세요."
  type        = string
  default     = "~/.ssh/muffin-ec2.pub"
}

variable "ssh_private_key_path" {
  description = "ssh_command 출력에 사용할 SSH 개인키 경로 (기본값은 ssh_public_key_path와 짝을 이룸)"
  type        = string
  default     = "~/.ssh/muffin-ec2"
}

variable "ssh_allowed_cidrs" {
  # GitHub Actions(CD)가 SSH로 배포하는데 러너 IP가 매번 랜덤이라 기본값은 전체 허용이다.
  # 고정 IP 러너/VPN/배스천을 쓰거나 SSM Session Manager로 전환하면 반드시 좁힐 것.
  description = "SSH(22) 접근을 허용할 CIDR 목록. 가능하면 승인된 네트워크로 좁히세요."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

# ---------- RDS ----------

variable "db_name" {
  description = "생성할 데이터베이스 이름"
  type        = string
  default     = "muffin"
}

variable "db_username" {
  description = "RDS 마스터 사용자명"
  type        = string
  default     = "muffin_admin"
}

variable "db_password" {
  description = "RDS 마스터 비밀번호 (tfvars에 커밋하지 말고 TF_VAR_db_password 환경변수로 넘기세요)"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS 스토리지 크기 (GB)"
  type        = number
  default     = 20
}

variable "db_backup_retention_period" {
  # AWS 무료 플랜은 백업 보관 기간에 상한이 있어 7일을 쓸 수 없다(FreeTierRestrictionError).
  # 그래서 기본값은 현재 계정에서 동작하는 1로 두되, 유료 전환 시 7 이상으로 올릴 것을 권장한다.
  # (더 긴 보존이 필요하면 AWS Backup 또는 별도 스냅샷 정책을 병행)
  description = "RDS 자동 백업 보관 기간(일). 유료 플랜에서는 7 이상 권장."
  type        = number
  default     = 1

  validation {
    condition     = var.db_backup_retention_period >= 0 && var.db_backup_retention_period <= 35
    error_message = "backup_retention_period는 0~35 사이여야 합니다."
  }
}

variable "db_engine_version" {
  # 고정한 마이너 8.4.10의 RDS 표준 지원은 2027-07-07까지다(8.4 시리즈 전체가 아니라 이 마이너 기준).
  # 이후 마이너는 auto_minor_version_upgrade(rds.tf, 미설정 = 기본 true)에 따라 유지보수 창에
  # 자동 상향된다. 특정 마이너로 고정하려면 이 기본값을 갱신하고 그 자동 상향을 끌 것.
  # 8.0.46 -> 8.4.10 업그레이드 검증 완료:
  #   - 로컬 MySQL 8.4.10에서 Flyway 마이그레이션 + 앱 부팅 통과
  #   - 스키마 전 테이블 InnoDB + utf8mb4 (8.4 제거 문법 없음)
  #   - muffin_admin 인증 플러그인을 mysql_native_password -> caching_sha2_password 전환 완료
  #     (8.4는 mysql_native_password가 기본 비활성)
  # RDS가 구버전 지원 종료 시 사용 가능한 버전은 아래로 확인:
  #   aws rds describe-db-engine-versions --engine mysql \
  #     --query "DBEngineVersions[?starts_with(EngineVersion,'8.4')].EngineVersion" --output text
  description = "MySQL 엔진 버전 (지원 종료 시 위 명령으로 확인 후 갱신)"
  type        = string
  default     = "8.4.10"
}

# ---------- ECR / CI ----------

variable "github_repository" {
  description = "GitHub Actions OIDC 신뢰 대상 레포지토리 (owner/repo). 이 레포의 워크플로우만 ECR push 역할을 assume 할 수 있다."
  type        = string
  default     = "money-pin/muffin-back"
}

