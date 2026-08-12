# 온박스 관측 수집기

앱 지표와 로그를 모아 Grafana Cloud로 보낸다. 저장·대시보드·알림은 전부 박스 밖에 있다.

## 왜 박스 밖인가

같은 호스트에 모니터링을 두면 **그 호스트가 죽는 순간 관측 주체도 같이 죽는다.** 정산이 안 돌아 사용자 자산이 갱신되지 않는 상황이 이 서비스의 1순위 장애인데, 하필 그때 아무것도 못 보게 된다. 박스 밖에 두면 데이터가 끊기는 것 자체가 신호가 된다.

## 구성

| 컨테이너 | 메모리 | 하는 일 |
| --- | --- | --- |
| `muffin-alloy` | ~120MB (상한 256MB) | 앱 `/actuator/prometheus` 스크레이프, 호스트 지표, RDS CloudWatch 지표, 도커 로그 수집 → Grafana Cloud |
| `muffin-mysqld-exporter` | ~20MB (상한 64MB) | RDS 상태값(커넥션 수, InnoDB 버퍼풀 히트율, 커밋/롤백) |

RDS 인스턴스 자체의 CPU/메모리/IOPS는 mysqld-exporter가 볼 수 없다(MySQL이 들고 있는 상태값만 읽는다). 그건 Alloy의 CloudWatch exporter가 **EC2 인스턴스 역할로 인증해서** 가져온다. Grafana Cloud의 CloudWatch 데이터소스를 쓰지 않은 이유는 그쪽이 만료 없는 액세스 키를 외부 서비스에 맡기는 방식이기 때문이다. 인스턴스 역할은 자격증명이 AWS 밖으로 나가지 않고 자동으로 교체된다.

## 사전 조건

- 앱에 `/actuator/prometheus`가 배포돼 있을 것
- SSM `/muffin/dev`에 파라미터가 있을 것 (하나라도 없으면 `env-from-ssm.sh`가 중단된다)
  - 직접 넣는 값: `GRAFANA_PROM_URL`, `GRAFANA_PROM_USER`, `GRAFANA_LOKI_URL`, `GRAFANA_LOKI_USER`, `GRAFANA_TOKEN`
  - `terraform apply`가 넣는 값: `DB_EXPORTER_USERNAME`, `DB_EXPORTER_PASSWORD`
  - 앱 배포가 이미 쓰고 있는 값: `DB_URL` (RDS 호스트를 여기서 뽑아낸다)

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

**설정만 고쳤을 때는 `up -d`가 아니라 `restart`다.** `config.alloy`는 바인드 마운트라, `up -d`는 이미지·명령어·볼륨 정의만 비교하고 마운트된 파일의 내용 변경은 보지 않는다. 파일은 새로 갔는데 Alloy가 옛 설정으로 계속 도는 상태가 된다.

```bash
./push-to-box.sh                      # 노트북에서
sudo docker compose restart alloy     # 박스에서
```

## 확인

```bash
cd /opt/muffin/monitoring
sudo docker compose ps                        # 두 컨테이너가 Up
sudo docker compose logs alloy | tail -30     # remote_write 에러가 없어야 함

# mysqld-exporter는 RDS 연결이 끊겨도 HTTP 200을 준다(자기 자신의 go_ 지표는 계속 나온다).
# 연결 성공 여부는 mysql_up으로만 알 수 있다. 1이어야 한다.
curl -s localhost:9104/metrics | grep '^mysql_up'
```

호스트 지표는 박스에서 확인할 수 없다. Alloy가 수집 즉시 Grafana Cloud로 보내고, 관리 UI(`12345`)에는 Alloy 자신의 내부 지표만 있다. Grafana Cloud의 Explore에서 아래가 다 나오면 세 갈래가 전부 붙은 것이다.

```promql
muffin_batch_job_last_success_timestamp_seconds   # 앱 (잡 14개, 라벨은 batch_job)
node_memory_MemAvailable_bytes                    # 호스트 (컨테이너가 아닌 2GB 기준 값이어야 함)
node_filesystem_avail_bytes{mountpoint="/"}       # 호스트 디스크
mysql_up                                          # RDS (MySQL 상태값)
aws_rds_freeable_memory_average                   # RDS (인스턴스 지표, 5분 간격)
```

배치 지표의 잡 이름 라벨은 `job`이 아니라 **`batch_job`**이다. `job`은 프로메테우스가 스크레이프 대상을 식별하는 데 쓰는 예약 라벨이라, 앱이 같은 이름을 내보내면 충돌해 `exported_job`으로 밀려난다. `job="muffin-backend"`는 "이 지표가 앱에서 왔다"는 뜻이다.

디스크 지표의 `mountpoint`는 `/rootfs`가 아니라 `/`다. `rootfs_path` 설정이 컨테이너가 읽은 경로에서 그 접두사를 떼어내 호스트 기준으로 정규화한다.

로그는 `{service="muffin"}` 으로 조회한다. `level`, `domain` 라벨로 걸러진다.

## 대시보드와 알림

| 디렉터리 | 내용 |
| --- | --- |
| `dashboards/` | Grafana 대시보드 정의 3개(배치 / API·앱 / DB). Grafana가 대시보드를 저장하는 JSON 형식 그대로다 |
| `alerting/` | 알림 룰 9개(배치 침묵, 앱·수집기 다운, DB 끊김, 디스크·메모리·스토리지 여유). Prometheus 규칙 파일 형식이라 Grafana UI에서 그대로 import된다 |

**이 파일들은 설계도지 동작하는 물건이 아니다.** Grafana Cloud에 올려야 실제 화면과 알림이 된다. **레포와 Grafana Cloud 사이에 자동 연결은 없다**(CD는 앱 이미지만 배포한다). 올리고 고치는 일은 관측 담당자가 한다.

팀원이 볼 곳은 Grafana의 대시보드 3개다. 알림은 디스코드로 온다.

| 대시보드 | 무엇을 보나 |
| --- | --- |
| Muffin / 배치 | 잡별 마지막 성공 이후 경과, 결과별 실행 추이, 소요시간, 배치 로그 |
| Muffin / API·앱 | RPS·성공률·상태코드, 엔드포인트별 응답시간과 호출·지연 순위, 처리량·포화, JVM·호스트, 에러 로그 |
| Muffin / DB | 커넥션 풀, MySQL 상태값, RDS 인스턴스 지표, 리포지토리 메서드 순위 |

느린 쿼리(1초 초과)는 Grafana가 아니라 **CloudWatch Logs**에 있다(`/aws/rds/instance/muffin-db/slowquery`). Alloy가 CloudWatch에서 가져오는 것은 지표뿐이고 로그는 경로가 다르다.

## 알아둘 것

- **`.env`는 커밋하지 않는다.** SSM에서 매번 생성한다.
- 배포 중에는 앱이 잠깐 내려가 스크레이프가 실패한다. 대시보드의 짧은 공백은 정상이다.
- 배포 중 이전 컨테이너가 `muffin-prev`로 잠시 남는데, 로그 라벨은 `service="muffin"`으로 정규화해 한 갈래로 모은다.
- Alloy 관리 UI(`127.0.0.1:12345`)는 루프백에만 열려 있다. 호스트 네트워크를 쓰므로 `0.0.0.0`으로 열면 외부에 노출된다.
- 수집기 자신의 로그는 Loki로 보내지 않는다. 앱 로그를 덮는 노이즈라 `docker compose logs`로 직접 본다.
