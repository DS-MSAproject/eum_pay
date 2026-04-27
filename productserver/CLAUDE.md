# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 빌드 및 실행 명령어

프로젝트 루트(`eum_pay/`)에서 실행:

```bash
# productserver 단독 빌드
./gradlew :productserver:build

# 테스트 스킵 빌드
./gradlew :productserver:build -x test

# Jib로 컨테이너 이미지 빌드
./gradlew :productserver:jibDockerBuild

# 컨테이너 레지스트리로 직접 푸시
./gradlew :productserver:jib
```

## 트러블슈팅 기록

### [2026-04-27] 내부 Feign 호출 시 401 오류

**증상**: orderserver의 `ProductCheckoutClient`가 `POST /product/checkout/validate`를 호출하면 401 반환.

**원인**: `spring-cloud-starter-vault-config` 또는 `spring-cloud-starter-netflix-eureka-client:4.0.3`이 `spring-security-web`/`spring-security-config`를 transitive하게 끌어들여 Spring Boot Security 자동 설정이 활성화됨. 커스텀 `SecurityConfig`가 없으면 기본값인 HTTP Basic 인증이 전체 엔드포인트에 적용됨.

**수정**: `src/main/resources/application.yml`에 Security 자동 구성 제외 추가.
```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
      - org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration
```

**재배포**: `./gradlew :productserver:jibDockerBuild` 후 컨테이너 재시작 필요.
