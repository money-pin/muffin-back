# 사전 조건: 이 이미지를 빌드하기 전에 jar가 만들어져 있어야 한다.
#   ./gradlew clean bootJar
#   docker build -t muffin-backend .
#
# jar 빌드를 Docker 안에서 하지 않는 이유: GitHub Actions의 Gradle 캐시를 그대로 활용하는 편이
# 훨씬 빠르고, 빌드 결과물(REST Docs 포함)도 CI와 동일하게 유지되기 때문이다.

# ---------- 1단계: 레이어드 jar 분해 ----------
# Spring Boot 3.3+ 의 tools jarmode로 jar를 계층별로 쪼갠다.
# 의존성 레이어는 버전이 바뀌어도 그대로라 ECR에 중복 저장되지 않는다(용량/전송 절약).
FROM eclipse-temurin:21-jre-alpine AS builder
WORKDIR /builder

ARG JAR_FILE=build/libs/*-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar

RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- 2단계: 런타임 ----------
FROM eclipse-temurin:21-jre-alpine

# 일 단위 정산/시세 기준일을 다루므로 컨테이너 타임존을 KST로 고정한다.
RUN apk add --no-cache tzdata \
    && cp /usr/share/zoneinfo/Asia/Seoul /etc/localtime \
    && echo "Asia/Seoul" > /etc/timezone

# 루트로 실행하지 않는다.
RUN addgroup -S muffin && adduser -S muffin -G muffin

WORKDIR /application

# 변경 빈도가 낮은 레이어부터 복사해야 캐시 적중률이 높다.
COPY --from=builder --chown=muffin:muffin /builder/extracted/dependencies/ ./
COPY --from=builder --chown=muffin:muffin /builder/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=muffin:muffin /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=muffin:muffin /builder/extracted/application/ ./

USER muffin

ENV TZ=Asia/Seoul
ENV SPRING_PROFILES_ACTIVE=prod

# t3.micro(1GB)에서 OOM Killer에 죽지 않도록 힙을 제한한다.
# 작은 힙에서는 SerialGC가 G1보다 오버헤드가 적다. 호스트의 swap 2GB가 안전망.
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseSerialGC -XX:MaxMetaspaceSize=192m -Duser.timezone=Asia/Seoul"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar application.jar"]
