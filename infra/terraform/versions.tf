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

  # 원격 state (state에 DB 비밀번호가 평문 저장되므로 암호화된 원격 backend 사용).
  #
  # ⚠️ backend는 변수를 못 쓰고, 아래 버킷이 "먼저" 존재해야 terraform init이 된다.
  #    최초 1회 아래 명령으로 부트스트랩한 뒤 init 하세요 (닭-달걀 문제 해소):
  #
  #   aws s3api create-bucket \
  #     --bucket muffin-tfstate-9f3c1a \
  #     --region ap-northeast-2 \
  #     --create-bucket-configuration LocationConstraint=ap-northeast-2
  #   aws s3api put-bucket-versioning \
  #     --bucket muffin-tfstate-9f3c1a \
  #     --versioning-configuration Status=Enabled
  #
  # 암호화는 별도 명령이 필요 없다: 2023-01 이후 생성되는 S3 버킷은 SSE-S3(AES256)가 기본 적용된다.
  #
  # 그 다음 `terraform init` 하면 로컬 state를 원격으로 옮길지 물어본다(yes).
  #
  # 락은 DynamoDB 테이블 없이 S3 자체 락파일(use_lockfile)을 쓴다.
  # Terraform 1.10+에서 S3 conditional write 기반으로 지원되며, 1.11부터 dynamodb_table은 deprecated.
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
