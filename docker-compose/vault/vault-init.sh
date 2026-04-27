#!/bin/sh

# 1. Vault 서버 시작
vault server -config=/vault/config/config.hcl > /vault/data/server.log 2>&1 &

echo "==> Vault 서버가 깨어나길 기다리는 중..."
until vault status -address=http://127.0.0.1:8200 2>&1 | grep -q "Initialized" || [ $? -eq 0 ]; do
    sleep 1
done

export VAULT_ADDR='http://127.0.0.1:8200'

# 2. 초기화 및 언씰 (keys.txt가 없을 때만 실행)
if [ ! -f /vault/data/keys.txt ]; then
    echo "==> 최초 실행: 금고를 생성하고 열쇠를 보관합니다..."
    vault operator init -key-shares=1 -key-threshold=1 > /vault/data/keys.txt

    TEMP_TOKEN=$(grep "Initial Root Token:" /vault/data/keys.txt | cut -d' ' -f4 | tr -d '\r')
    K1=$(grep "Unseal Key 1:" /vault/data/keys.txt | cut -d' ' -f4 | tr -d '\r')

    vault operator unseal "$K1"

    VAULT_TOKEN="$TEMP_TOKEN" vault token create -id="eum-root" -policy="root"

    export VAULT_TOKEN="eum-root"
    vault secrets enable -path=secret kv-v2 || true
fi

# 3. 상시 봉인 해제
K1=$(grep "Unseal Key 1:" /vault/data/keys.txt | cut -d' ' -f4 | tr -d '\r')
vault operator unseal "$K1"

export VAULT_TOKEN="eum-root"
sleep 2

# 4. eum 데이터 주입
echo "==> eum 비밀 데이터들을 금고에 채우는 중.."

vault kv put secret/application     @/vault/data/application.json     || true
vault kv put secret/dseum-eureka    @/vault/data/dseum-eureka.json    || true
vault kv put secret/dseum-product   @/vault/data/dseum-product.json   || true
vault kv patch secret/dseum-product @/vault/data/dseum-product-categories.json || true
vault kv put secret/dseum-inventory @/vault/data/dseum-inventory.json || true
vault kv put secret/dseum-cart      @/vault/data/dseum-cart.json      || true
vault kv put secret/dseum-order     @/vault/data/dseum-order.json     || true
vault kv put secret/dseum-payment   @/vault/data/dseum-payment.json   || true
vault kv put secret/dseum-search    @/vault/data/dseum-search.json    || true
vault kv patch secret/dseum-search  @/vault/data/dseum-product-categories.json || true
vault kv patch secret/dseum-search  @/vault/data/dseum-search-brand-story.json || true
vault kv put secret/dseum-board     @/vault/data/dseum-board.json     || true
vault kv put secret/dseum-review    @/vault/data/dseum-review.json    || true
vault kv put secret/authserver      @/vault/data/dseum-authserver.json || true
vault kv put secret/dseum-rag       @/vault/data/dseum-rag.json       || true

GEMINI_KEY_PATH="/vault/data/gemini_key.txt" # 도커 볼륨 마운트 경로 기준
RAW_KEY=""

if [ -f "$GEMINI_KEY_PATH" ]; then
    RAW_KEY=$(cat "$GEMINI_KEY_PATH" | tr -d '\n\r ')
fi

if [ -n "$RAW_KEY" ]; then
    echo "==> [보안] 제미나이 API 키를 Vault(secret/dseum-rag)에 반영합니다..."
    vault kv patch secret/dseum-rag \
        GEMINI_API_KEY="$RAW_KEY" \
        gemini.api-key="$RAW_KEY" \
        rag.ai.api-key="$RAW_KEY"

    echo "==> [완료] 제미나이 API 키 Vault 반영 성공!"
else
    echo "==> [경고] /vault/data/gemini_key.txt 값을 찾을 수 없어 GEMINI_API_KEY 반영을 건너뜁니다."
fi

# vault-init.sh 내부
echo "==> Gateway SSL 데이터를 금고에 보관합니다..."

# 💡 docker-compose에서 볼륨 마운트된 경로를 확인하세요.
# 보통 /vault/data/cert_base64.txt 경로에 파일이 있어야 합니다.
ENCODED_CERT=$(cat /vault/data/cert_base64.txt | tr -d '\n\r')

vault kv put secret/dseum-gateway \
    keystore_data="$ENCODED_CERT" \
    keystore_password="eum1234" \
    key_alias="certificate" \
    key_store_type="PKCS12"

# 💡 물리 파일 복원 (스프링 로딩용)
# /vault/data 폴더가 게이트웨이와 공유되고 있다면 바로 사용 가능합니다.
echo "$ENCODED_CERT" | base64 -d > /vault/data/certificate.p12
chmod 644 /vault/data/certificate.p12

# ---------------------------------------------------------
# 4-1. IAM 및 S3 환경 변수 주입 (AWS_KEY_PATH 등)
# ---------------------------------------------------------
AWS_ENV_PATH="/vault/data/.env" # 배포할 때 파일명 맞춰주세요.

if [ -f "$AWS_ENV_PATH" ]; then
    echo "==> [보안] AWS IAM 키를 읽어 금고에 저장합니다..."

    # 파일에서 값 읽기 (변수명=값 형태 가정)
    # 예: ACCESS_KEY=ABC...
    #     SECRET_KEY=XYZ...
    ACCESS_KEY=$(grep "^AWS_ACCESS_KEY=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    SECRET_KEY=$(grep "^AWS_SECRET_KEY=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    AWS_REGION=$(grep "^AWS_REGION=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    S3_BUCKET=$(grep "^S3_BUCKET=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    CDN_ENABLED=$(grep "^CDN_ENABLED=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    CDN_DOMAIN=$(grep "^CDN_DOMAIN=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')

    # 💡 [무결성 포인트] 모든 서버가 공통으로 참조하는 application 경로에 패치
    vault kv patch secret/application \
        cloud.aws.credentials.access-key="$ACCESS_KEY" \
        cloud.aws.credentials.secret-key="$SECRET_KEY" \
        cloud.aws.region.static="$AWS_REGION" \
        cloud.aws.s3.bucket="$S3_BUCKET" \
        cloud.aws.cdn.enabled="${CDN_ENABLED:-false}" \
        cloud.aws.cdn.domain="$CDN_DOMAIN"

    echo "==> [완료] AWS IAM 및 S3 설정 주입 성공!"

    # Toss 시크릿 키 — dseum-payment.json의 플레이스홀더를 실제값으로 덮어씁니다.
    TOSS_SECRET_KEY=$(grep "^TOSS_SECRET_KEY=" "$AWS_ENV_PATH" | cut -d'=' -f2- | tr -d '\n\r ')
    if [ -n "$TOSS_SECRET_KEY" ]; then
        echo "==> [보안] Toss 시크릿 키를 금고에 저장합니다..."
        vault kv patch secret/dseum-payment \
            "payment.toss.secret-key"="$TOSS_SECRET_KEY"
        echo "==> [완료] Toss 시크릿 키 패치 성공!"
    else
        echo "==> [경고] TOSS_SECRET_KEY가 .env에 없어 결제 서비스가 정상 동작하지 않을 수 있습니다."
    fi
else
    echo "==> [경고] $AWS_ENV_PATH 파일을 찾을 수 없어 AWS/Toss 설정 주입을 건너뜁니다."
fi

# 5. RSA 키쌍 자동 생성 → secret/auth/rsa 에 저장
echo "==> RSA 키쌍 생성 중..."
which openssl || apk add --no-cache openssl 2>/dev/null || apt-get install -y openssl 2>/dev/null || true

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/private_key.pem 2>/dev/null
openssl rsa -pubout -in /tmp/private_key.pem -out /tmp/public_key.pem 2>/dev/null

PRIVATE_KEY=$(cat /tmp/private_key.pem)
PUBLIC_KEY=$(cat /tmp/public_key.pem)

vault kv put secret/auth/rsa private_key="$PRIVATE_KEY" public_key="$PUBLIC_KEY"

rm -f /tmp/private_key.pem /tmp/public_key.pem

echo "==> RSA 키쌍 생성 및 Vault 저장 완료"
echo "==> [완료] VAULT READY (HTTP Mode, Token: eum-root)"

wait
