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
mkdir -p /usr/local/lib/docker/cli-plugins
curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 앱 시크릿은 서버에 파일로 두지 않고 SSM Parameter Store에서 배포 시점에 조회한다.
# (EC2의 IAM Role로 인증하므로 자격증명 파일이 필요 없다)

# 실제 컨테이너 실행(docker run / docker compose up)은
# 여기서 하지 않고 CI/CD(GitHub Actions -> ECR push -> SSH로 pull & restart)에서 처리하는 걸 권장합니다.
# user_data는 인스턴스 최초 생성 시 한 번만 실행되어 코드 업데이트 시 재실행되지 않기 때문입니다.
