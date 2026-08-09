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
  default     = "dev"
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
  # Spring Boot + Docker 운영 여유를 확보하기 위해 2GB 메모리의 t3.small을 기본으로 사용한다.
  description = "EC2 인스턴스 타입 (기본 t3.small)"
  type        = string
  default     = "t3.small"
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

variable "app_db_username" {
  description = "애플리케이션 전용 MySQL 사용자명(RDS master 계정과 분리)"
  type        = string
  default     = "muffin_app"

  validation {
    condition = (
      can(regex("^[A-Za-z][A-Za-z0-9_]{2,31}$", var.app_db_username))
      && var.app_db_username != var.db_username
    )
    error_message = "app_db_username은 영문자로 시작하는 3~32자 영문/숫자/_ 조합이며 db_username과 달라야 합니다."
  }
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
  # 운영 관점에서는 7일 이상이 바람직하지만, 현재 계정이 AWS 프리 플랜이라
  # 그 이상을 요청하면 ModifyDBInstance가 FreeTierRestrictionError로 거부된다.
  # 실제 인스턴스도 1일로 운영돼 왔어서, 설정을 현실에 맞춘다.
  #
  # ⚠️ PITR 복구 범위가 1일뿐이다. 데이터를 지우거나 스키마를 바꾸는 작업 전에는
  #    수동 스냅샷을 먼저 찍을 것:
  #      aws rds create-db-snapshot --db-instance-identifier muffin-db \
  #        --db-snapshot-identifier muffin-db-before-<작업명>-<날짜>
  #    계정 플랜을 올리면 7 이상으로 되돌린다.
  description = "RDS 자동 백업 보관 기간(일). 프리 플랜 제약으로 현재 1일."
  type        = number
  default     = 1

  validation {
    condition     = var.db_backup_retention_period >= 1 && var.db_backup_retention_period <= 35
    error_message = "backup_retention_period는 1~35 사이여야 합니다."
  }
}

variable "db_engine_version" {
  # 고정한 마이너 8.4.10의 RDS 표준 지원은 2027-07-07까지다(8.4 시리즈 전체가 아니라 이 마이너 기준).
  # 이후 마이너는 auto_minor_version_upgrade(rds.tf, 명시적 true)에 따라 유지보수 창에
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


variable "exporter_db_username" {
  description = "지표 수집 전용 DB 계정 이름 (상태값 조회 권한만 부여)"
  type        = string
  default     = "muffin_exporter"
}
