#!/usr/bin/env bash
# 로컬 MySQL에 떠 있는 muffin 스키마(그동안 ddl-auto: update로 관리되어 온 것)를
# Flyway 초기 마이그레이션(V1__init.sql)으로 추출합니다.
#
# ⚠️ 적용된 마이그레이션은 불변입니다.
#    운영에 한 번이라도 배포된 뒤에는 V1을 다시 생성하지 마세요.
#    Flyway가 flyway_schema_history에 체크섬을 저장하므로, 파일이 바뀌면
#    다음 배포에서 체크섬 불일치로 마이그레이션이 실패합니다.
#    스키마 변경은 V2__xxx.sql, V3__xxx.sql 로 "추가"해서 관리합니다.
#
#    첫 배포 전(운영 DB가 아직 없는 시기)에 한해 --force로 재생성할 수 있습니다.
#
# 사전 조건: 로컬 muffin DB가 최신 상태로 떠 있어야 함 (앱을 한 번 update 모드로 띄워둔 상태)
#
# 사용법: ./scripts/generate-flyway-baseline.sh [DB_NAME] [--force]
set -euo pipefail

FORCE=false
DB_NAME="muffin"

for arg in "$@"; do
    case "$arg" in
    --force) FORCE=true ;;
    *) DB_NAME="$arg" ;;
    esac
done

DB_USER="${DB_USERNAME:-root}"
OUTPUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/db/migration"
OUTPUT_FILE="$OUTPUT_DIR/V1__init.sql"

mkdir -p "$OUTPUT_DIR"

if [ -e "$OUTPUT_FILE" ] && [ "$FORCE" = false ]; then
    cat >&2 <<MSG
중단: 이미 $OUTPUT_FILE 가 존재합니다.

적용된 마이그레이션은 수정하지 않습니다. 스키마 변경이 있다면 V1을 덮어쓰지 말고
새 버전 파일을 추가하세요:

    $OUTPUT_DIR/V2__<변경_설명>.sql

아직 운영에 배포한 적이 없어 V1을 재생성해도 안전한 경우에만 --force를 붙이세요.
MSG
    exit 1
fi

# 덤프 실패 시 기존 파일이 깨지지 않도록 임시 파일에 받은 뒤 성공했을 때만 교체한다.
# (리다이렉션을 대상 파일에 바로 걸면 mysqldump가 실패해도 파일이 이미 비워진다)
TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

mysqldump \
    --no-data \
    --skip-comments \
    --skip-add-locks \
    --skip-set-charset \
    --column-statistics=0 \
    -u "$DB_USER" -p \
    "$DB_NAME" >"$TMP_FILE"

if [ ! -s "$TMP_FILE" ]; then
    echo "중단: 덤프 결과가 비어 있습니다. DB 이름과 접속 정보를 확인하세요." >&2
    exit 1
fi

mv "$TMP_FILE" "$OUTPUT_FILE"
trap - EXIT

echo "생성됨: $OUTPUT_FILE"
echo ""
echo "확인할 것:"
echo "  1. AUTO_INCREMENT=<큰 숫자> 부분 - 초기 마이그레이션이라면 지우는 걸 권장"
echo "  2. flyway_schema_history 테이블이 덤프에 섞여 있지 않은지"
echo "  3. --column-statistics=0 옵션을 mysqldump가 인식 못 하면 그 줄만 지우고 재실행"
echo ""
echo "다음: 빈 DB에 이 마이그레이션만 적용한 뒤 ddl-auto=validate 로 기동해 검증하세요."
