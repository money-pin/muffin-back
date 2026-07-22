terraform {
  # S3 backend의 use_lockfile(네이티브 락)이 1.10부터 지원되므로 하한을 1.10으로 둔다.
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # state에 DB 비밀번호가 평문으로 저장되므로 암호화된 원격 backend를 쓴다.
  # ⚠️ 이 버킷은 Terraform이 만들어주지 않는다 — 새 환경에서는 버킷(버저닝 포함)을 먼저 만들고 init 할 것.
  #    backend 블록은 변수를 쓸 수 없어 버킷명이 하드코딩돼 있다.
  # 락은 DynamoDB 없이 S3 락파일을 쓴다 (use_lockfile, Terraform 1.10+ 필요).
  backend "s3" {
    bucket       = "muffin-tfstate-9f3c1a"
    key          = "muffin-back/terraform.tfstate"
    region       = "ap-northeast-2"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}
