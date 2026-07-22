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

variable "db_engine_version" {
  # RDS는 구버전을 주기적으로 지원 종료하므로, 사용 불가 에러가 나면 아래로 확인 후 갱신한다:
  #   aws rds describe-db-engine-versions --engine mysql \
  #     --query "DBEngineVersions[?starts_with(EngineVersion,'8.0')].EngineVersion" --output text
  description = "MySQL 엔진 버전 (지원 종료 시 위 명령으로 확인 후 갱신)"
  type        = string
  default     = "8.0.46"
}

# ---------- ECR / CI ----------

variable "github_repository" {
  description = "GitHub Actions OIDC 신뢰 대상 레포지토리 (owner/repo). 이 레포의 워크플로우만 ECR push 역할을 assume 할 수 있다."
  type        = string
  default     = "money-pin/muffin-back"
}

# ---------- S3 ----------

variable "s3_bucket_prefix" {
  description = "S3 버킷 이름 접두사 (전역 유니크해야 해서 뒤에 랜덤 suffix가 붙습니다)"
  type        = string
  default     = "muffin-app-storage"
}
