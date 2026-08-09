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

# 값을 먼저 전부 대입해 둔다. echo "KEY=$(need ...)" 로 바로 쓰면 need가 실패해도
# echo는 성공해서, 빈 값이 든 .env를 만들어 놓고 스크립트가 0으로 끝난다.
# 그러면 수집기는 인증 실패로 조용히 아무것도 못 보내는데 실행 로그만 정상으로 보인다.
# 대입은 set -e가 잡으므로 파라미터가 하나라도 없으면 여기서 멈춘다.
grafana_prom_url="$(need GRAFANA_PROM_URL)"
grafana_prom_user="$(need GRAFANA_PROM_USER)"
grafana_loki_url="$(need GRAFANA_LOKI_URL)"
grafana_loki_user="$(need GRAFANA_LOKI_USER)"
grafana_token="$(need GRAFANA_TOKEN)"
exporter_username="$(need DB_EXPORTER_USERNAME)"
exporter_password="$(need DB_EXPORTER_PASSWORD)"

# RDS 엔드포인트는 별도 파라미터가 없고 JDBC URL 안에 들어 있다.
# jdbc:mysql://호스트:3306/스키마?... 에서 호스트만 떼어낸다.
db_url="$(need DB_URL)"
db_host="$(printf '%s' "$db_url" | sed -E 's|^jdbc:mysql://([^:/?]+).*|\1|')"
if [ -z "$db_host" ] || [ "$db_host" = "$db_url" ]; then
  echo "ERROR: DB_URL에서 호스트를 뽑지 못했습니다. jdbc:mysql://호스트:포트/... 형식이어야 합니다." >&2
  exit 1
fi

{
  echo "GRAFANA_PROM_URL=$grafana_prom_url"
  echo "GRAFANA_PROM_USER=$grafana_prom_user"
  echo "GRAFANA_LOKI_URL=$grafana_loki_url"
  echo "GRAFANA_LOKI_USER=$grafana_loki_user"
  echo "GRAFANA_TOKEN=$grafana_token"
  echo "DB_EXPORTER_USERNAME=$exporter_username"
  echo "MYSQLD_EXPORTER_PASSWORD=$exporter_password"
  echo "DB_HOST=$db_host"
} > "$tmp"

mv "$tmp" "$OUT"
trap - EXIT
chmod 600 "$OUT"
echo "생성 완료: $OUT (값은 출력하지 않음)"
