#!/bin/bash
# EC2 최초 부팅 시 1회 실행됨 (Amazon Linux 2023 기준)
set -eux

# ---- swap 2GB ----
# JVM(Spring Boot)이 메모리 스파이크로 OOM Killer에 죽는 것을 막기 위한 안전망.
# 특히 t3.micro(1GB, 프리티어)에서는 swap이 사실상 필수. 앱 컨테이너 힙은 -Xmx512m 수준으로 제한 권장.
if [ ! -f /swapfile ]; then
  dd if=/dev/zero of=/swapfile bs=1M count=2048
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

# ---- SSH 하드닝 ----
# 22번 포트가 인터넷에 열려 있으므로(CD 러너 IP가 고정되지 않음) 키 기반 인증만 허용한다.
# AL2023 기본값도 동일하지만, 기본값에 의존하지 않고 명시적으로 못박는다.
cat > /etc/ssh/sshd_config.d/99-muffin-hardening.conf <<'SSHD'
PasswordAuthentication no
PermitRootLogin no
ChallengeResponseAuthentication no
KbdInteractiveAuthentication no
PubkeyAuthentication yes
SSHD
systemctl restart sshd

dnf update -y
dnf install -y docker

systemctl enable --now docker
usermod -aG docker ec2-user

# docker compose plugin
# latest는 재부팅/재생성 시점마다 다른 버전이 깔려 재현성이 없으므로 버전을 고정하고,
# 다운로드 실패(-f)와 체크섬 불일치 시 즉시 중단한다(set -e). 검증 통과 후에만 설치한다.
COMPOSE_VERSION=v5.3.1
COMPOSE_URL="https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-x86_64"
mkdir -p /usr/local/lib/docker/cli-plugins
compose_tmp="$(mktemp)"
curl -fSL --retry 3 --retry-delay 2 "$COMPOSE_URL" -o "$compose_tmp"
curl -fSL --retry 3 --retry-delay 2 "${COMPOSE_URL}.sha256" -o "${compose_tmp}.sha256"
# 공식 체크섬 파일은 릴리스 자산명 기준이라, 해시만 뽑아 임시 파일명에 맞춰 검증한다.
echo "$(awk '{print $1}' "${compose_tmp}.sha256")  ${compose_tmp}" | sha256sum -c -
install -m 755 "$compose_tmp" /usr/local/lib/docker/cli-plugins/docker-compose
rm -f "$compose_tmp" "${compose_tmp}.sha256"

# ---- Nginx 리버스 프록시 + Certbot ----
# api.muffin.ai.kr:443 -> 로컬 8080(앱 컨테이너). 인증서는 여기서 발급하지 않는다.
# (도메인 DNS가 EIP를 가리켜야 발급이 가능한데 user_data는 부팅 시 1회만 실행되므로,
#  DNS 전파가 안 끝난 시점에 실행되면 실패한다. 최초 발급은 DNS 전파 확인 후 SSH로 수동 실행.)
dnf install -y nginx augeas-libs
python3 -m venv /opt/certbot
/opt/certbot/bin/pip install --upgrade pip
/opt/certbot/bin/pip install certbot certbot-nginx
ln -sf /opt/certbot/bin/certbot /usr/bin/certbot

cat > /etc/nginx/conf.d/muffin.conf <<'NGINX'
server {
    listen 80;
    server_name api.muffin.ai.kr;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
NGINX

systemctl enable --now nginx

# 인증서 자동 갱신 (하루 2회 체크, certbot이 만료 30일 이내일 때만 실제 갱신)
cat > /etc/systemd/system/certbot-renew.service <<'UNIT'
[Unit]
Description=Certbot renewal

[Service]
Type=oneshot
ExecStart=/usr/bin/certbot renew --quiet --deploy-hook "systemctl reload nginx"
UNIT

cat > /etc/systemd/system/certbot-renew.timer <<'UNIT'
[Unit]
Description=Run certbot renew twice daily

[Timer]
OnCalendar=*-*-* 03,15:00:00
RandomizedDelaySec=1800
Persistent=true

[Install]
WantedBy=timers.target
UNIT

systemctl enable --now certbot-renew.timer

# 앱 시크릿은 서버에 파일로 두지 않고 SSM Parameter Store에서 배포 시점에 조회한다.
# (EC2의 IAM Role로 인증하므로 자격증명 파일이 필요 없다)

# 실제 컨테이너 실행(docker run / docker compose up)은
# 여기서 하지 않고 CI/CD(GitHub Actions -> ECR push -> SSH로 pull & restart)에서 처리하는 걸 권장합니다.
# user_data는 인스턴스 최초 생성 시 한 번만 실행되어 코드 업데이트 시 재실행되지 않기 때문입니다.
#
# ---- HTTPS 인증서 최초 발급 (DNS 전파 완료 후, SSH로 1회 수동 실행) ----
# sudo certbot --nginx -d api.muffin.ai.kr --non-interactive --agree-tos -m <your-email>
# certbot-nginx 플러그인이 위 conf의 443 서버 블록 + HTTP->HTTPS 리다이렉트를 자동으로 추가해준다.
# 갱신은 위 certbot-renew.timer가 하루 2회 자동으로 체크한다.
