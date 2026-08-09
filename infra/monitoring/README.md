# 온박스 관측 수집기

앱 지표와 로그를 모아 Grafana Cloud로 보낸다. 저장·대시보드·알림은 전부 박스 밖에 있다.

## 왜 박스 밖인가

같은 호스트에 모니터링을 두면 **그 호스트가 죽는 순간 관측 주체도 같이 죽는다.** 정산이 안 돌아 사용자 자산이 갱신되지 않는 상황이 이 서비스의 1순위 장애인데, 하필 그때 아무것도 못 보게 된다. 박스 밖에 두면 데이터가 끊기는 것 자체가 신호가 된다.

## 구성

| 컨테이너 | 메모리 | 하는 일 |
| --- | --- | --- |
| `muffin-alloy` | ~120MB (상한 256MB) | 앱 `/actuator/prometheus` 스크레이프, 호스트 지표, 도커 로그 수집 → Grafana Cloud |
| `muffin-mysqld-exporter` | ~20MB (상한 64MB) | RDS 상태값(커넥션 수, InnoDB 버퍼풀 히트율, 커밋/롤백) |

RDS 인스턴스 자체의 CPU/메모리/IOPS는 exporter가 볼 수 없다. CloudWatch가 답한다.

## 사전 조건

- 앱에 `/actuator/prometheus`가 배포돼 있을 것
- SSM `/muffin/dev`에 파라미터가 있을 것: `GRAFANA_PROM_URL`, `GRAFANA_PROM_USER`, `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_USER`, `GRAFANA_TOKEN`
- `terraform apply`로 exporter DB 계정이 생성돼 있을 것 (`DB_EXPORTER_USERNAME` / `DB_EXPORTER_PASSWORD`)

## 실행

박스에는 레포가 없고 SSH도 열려 있지 않다. 배포와 같은 경로(SSM)로 파일을 올린다.

인스턴스 ID는 적어두지 않는다. `user_data` 변경 등으로 인스턴스가 교체되면 ID가 바뀌어, 적어둔 값은 사라진 박스를 가리키게 된다. 태그로 찾는다.

```bash
export AWS_PROFILE=muffin

# 1) 노트북에서: 설정 파일을 /opt/muffin/monitoring 으로 전송 (대상은 태그로 자동 조회)
./push-to-box.sh

# 2) 박스에서: 시크릿을 받아 기동
INSTANCE_ID="$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=muffin-backend" "Name=instance-state-name,Values=running" \
  --region ap-northeast-2 --query "Reservations[].Instances[].InstanceId" --output text)"
aws ssm start-session --target "$INSTANCE_ID" --region ap-northeast-2

cd /opt/muffin/monitoring
sudo ./env-from-ssm.sh     # SSM에서 .env 생성 (값은 화면에 찍지 않는다)
sudo docker compose up -d
```

인스턴스가 교체되면 이 절차를 그대로 다시 실행한다(박스 위의 설정과 `.env`는 함께 사라진다).

## 확인

```bash
docker compose ps                       # 두 컨테이너가 Up
docker compose logs alloy | tail -30    # remote_write 에러가 없어야 함
curl -s localhost:9104/metrics | head   # mysqld-exporter 응답
```

Grafana Cloud의 Explore에서 아래가 나오면 연결된 것이다.

```promql
muffin_batch_job_last_success_timestamp_seconds
```

로그는 `{service="muffin"}` 으로 조회한다. `level`, `domain` 라벨로 걸러진다.

## 알아둘 것

- **`.env`는 커밋하지 않는다.** SSM에서 매번 생성한다.
- 배포 중에는 앱이 잠깐 내려가 스크레이프가 실패한다. 대시보드의 짧은 공백은 정상이다.
- 배포 중 이전 컨테이너가 `muffin-prev`로 잠시 남는데, 로그 라벨은 `service="muffin"`으로 정규화해 한 갈래로 모은다.
- Alloy 관리 UI(`127.0.0.1:12345`)는 루프백에만 열려 있다. 호스트 네트워크를 쓰므로 `0.0.0.0`으로 열면 외부에 노출된다.
- 수집기 자신의 로그는 Loki로 보내지 않는다. 앱 로그를 덮는 노이즈라 `docker compose logs`로 직접 본다.
