#!/bin/bash
# 수집기 설정 파일을 EC2로 올린다.
#
# 박스에는 레포가 없다(CD는 도커 이미지만 넘긴다). SSH도 열려 있지 않아 scp를 쓸 수 없어서,
# 배포와 같은 경로인 SSM Run Command로 파일을 심는다. 내용은 base64로 감싸 개행/따옴표가
# 셸을 타면서 깨지는 것을 막는다.
#
# 인스턴스 ID는 태그로 찾는다. user_data 변경 등으로 인스턴스가 교체되면 ID가 바뀌는데,
# 하드코딩해 두면 사라진 ID를 가리켜 조용히 실패한다(cd.yml도 같은 이유로 태그를 쓴다).
#
# 사용: AWS_PROFILE=muffin ./push-to-box.sh            (태그로 자동 조회)
#       AWS_PROFILE=muffin ./push-to-box.sh i-0abc...  (직접 지정)
set -euo pipefail

REGION="${AWS_REGION:-ap-northeast-2}"
REMOTE_DIR="/opt/muffin/monitoring"
INSTANCE_TAG="${INSTANCE_TAG:-muffin-backend}"

INSTANCE_ID="${1:-}"
if [ -z "$INSTANCE_ID" ]; then
  INSTANCE_ID="$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=$INSTANCE_TAG" "Name=instance-state-name,Values=running" \
    --region "$REGION" \
    --query "Reservations[].Instances[].InstanceId" \
    --output text)"
fi

# 여러 개가 잡히면(태그 중복 등) 엉뚱한 박스에 심을 수 있으므로 정확히 1개일 때만 진행한다.
if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" = "None" ] || [ "$(printf '%s' "$INSTANCE_ID" | wc -w)" -ne 1 ]; then
  echo "ERROR: 실행 중인 대상 인스턴스(tag:Name=$INSTANCE_TAG)가 정확히 1개여야 합니다. found='$INSTANCE_ID'" >&2
  exit 1
fi
echo "대상 인스턴스: $INSTANCE_ID"

here="$(cd "$(dirname "$0")" && pwd)"

b64() { base64 < "$1" | tr -d '\n'; }

# SSM 문서의 commands는 "줄 배열"이다. 한 문자열에 개행을 담아 --parameters 축약
# 문법(commands=...)으로 넘기면 CLI가 이스케이프를 해석하지 않아 개행이 사라지고
# 모든 명령이 한 줄로 붙어버린다. cd.yml과 같이 JSON 파일로 넘긴다.
params="$(mktemp)"
trap 'rm -f "$params"' EXIT

{
  echo "set -euo pipefail"
  echo "mkdir -p '$REMOTE_DIR/alloy'"
  echo "echo '$(b64 "$here/compose.yml")' | base64 -d > '$REMOTE_DIR/compose.yml'"
  echo "echo '$(b64 "$here/alloy/config.alloy")' | base64 -d > '$REMOTE_DIR/alloy/config.alloy'"
  echo "echo '$(b64 "$here/env-from-ssm.sh")' | base64 -d > '$REMOTE_DIR/env-from-ssm.sh'"
  echo "chmod +x '$REMOTE_DIR/env-from-ssm.sh'"
  echo "ls -l '$REMOTE_DIR' '$REMOTE_DIR/alloy'"
} | jq -Rn '{commands: [inputs]}' > "$params"

command_id="$(aws ssm send-command \
  --instance-ids "$INSTANCE_ID" \
  --document-name "AWS-RunShellScript" \
  --region "$REGION" \
  --parameters "file://$params" \
  --query "Command.CommandId" \
  --output text)"

echo "전송 명령 ID: $command_id"
aws ssm wait command-executed --command-id "$command_id" --instance-id "$INSTANCE_ID" --region "$REGION" || true
aws ssm get-command-invocation \
  --command-id "$command_id" \
  --instance-id "$INSTANCE_ID" \
  --region "$REGION" \
  --query "{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}" \
  --output json
