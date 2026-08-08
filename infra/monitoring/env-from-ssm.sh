#!/bin/bash
# SSM Parameter Store에서 수집기용 .env를 만든다.
#
# 시크릿을 레포나 서버 파일로 들고 다니지 않기 위해서다. cd.yml이 앱 시크릿을
# 다루는 방식과 같고, EC2 인스턴스 역할이 /muffin/dev 경로 읽기 권한을 이미 갖고 있다.
#
# 사용: 이 스크립트가 있는 디렉터리에서 ./env-from-ssm.sh
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
SSM_PATH="${SSM_PATH:-/muffin/dev}"
OUT="$(cd "$(dirname "$0")" && pwd)/.env"

need() {
  aws ssm get-parameter --name "$SSM_PATH/$1" --with-decryption \
    --region "$REGION" --query "Parameter.Value" --output text
}

umask 077
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

{
  echo "GRAFANA_PROM_URL=$(need GRAFANA_PROM_URL)"
  echo "GRAFANA_PROM_USER=$(need GRAFANA_PROM_USER)"
  echo "GRAFANA_LOKI_URL=$(need GRAFANA_LOKI_URL)"
  echo "GRAFANA_LOKI_USER=$(need GRAFANA_LOKI_USER)"
  echo "GRAFANA_TOKEN=$(need GRAFANA_TOKEN)"
  echo "DB_EXPORTER_USERNAME=$(need DB_EXPORTER_USERNAME)"
  echo "MYSQLD_EXPORTER_PASSWORD=$(need DB_EXPORTER_PASSWORD)"

  # RDS 엔드포인트는 별도 파라미터가 없고 JDBC URL 안에 들어 있다.
  # jdbc:mysql://호스트:3306/스키마?... 에서 호스트만 떼어낸다.
  db_url="$(need DB_URL)"
  echo "DB_HOST=$(printf '%s' "$db_url" | sed -E 's|^jdbc:mysql://([^:/?]+).*|\1|')"
} > "$tmp"

mv "$tmp" "$OUT"
trap - EXIT
chmod 600 "$OUT"
echo "생성 완료: $OUT (값은 출력하지 않음)"
