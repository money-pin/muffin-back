#!/usr/bin/env bash
# 로컬 MySQL에 떠 있는 muffin 스키마(그동안 ddl-auto: update로 관리되어 온 것)를
# Flyway 초기 마이그레이션(V1__init.sql)으로 추출합니다.
#
# 사전 조건: 로컬 muffin DB가 최신 상태로 떠 있어야 함 (앱을 한 번 update 모드로 띄워둔 상태)
#
# 사용법: ./scripts/generate-flyway-baseline.sh [DB_NAME]
set -euo pipefail

DB_NAME="${1:-muffin}"
DB_USER="${DB_USERNAME:-root}"
OUTPUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/db/migration"
OUTPUT_FILE="$OUTPUT_DIR/V1__init.sql"

mkdir -p "$OUTPUT_DIR"

mysqldump \
  --no-data \
  --skip-comments \
  --skip-add-locks \
  --skip-set-charset \
  --column-statistics=0 \
  -u "$DB_USER" -p \
  "$DB_NAME" > "$OUTPUT_FILE"

echo "생성됨: $OUTPUT_FILE"
echo ""
echo "확인할 것:"
echo "  1. AUTO_INCREMENT=<큰 숫자> 부분 - 초기 마이그레이션이라면 지우는 걸 권장"
echo "  2. flyway_schema_history 테이블이 덤프에 섞여 있지 않은지"
echo "  3. --column-statistics=0 옵션을 mysqldump가 인식 못 하면 그 줄만 지우고 재실행"
