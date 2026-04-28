# RagServer API Spec (Frontend)

이 문서는 `ragserver` 기준 프론트엔드 연동 명세입니다.  
기본적으로 게이트웨이 경로(`/api/v1/...`)를 기준으로 작성했습니다.

## 1) 라우팅 요약 (Gateway 기준)

- `POST /api/v1/rag/chat`
- `GET /api/v1/rag/sessions/{sessionId}`
- `POST /api/v1/rag/search`
- `POST /api/v1/rag/documents` (multipart/form-data)
- `GET /api/v1/rag/documents/{documentId}`
- `GET /api/v1/rag/documents/{documentId}/chunks`

게이트웨이 리라이트:
- `/api/v1/rag/** -> /rag/**`

## 2) 공통 응답 포맷

성공 응답(`ApiResponse<T>`)의 실제 JSON 마샬링 형태:

```json
{
  "code": "SUCCESS",
  "message": "Request processed successfully.",
  "data": {},
  "timestamp": "2026-04-28T03:41:22.1054244Z"
}
```

에러 응답(`ErrorResponse`)의 형태:

```json
{
  "code": "COMMON-401",
  "message": "Validation failed.",
  "path": "/rag/chat",
  "timestamp": "2026-04-28T03:41:22.1054244Z"
}
```

주요 에러 코드:
- `COMMON-400` 잘못된 요청
- `COMMON-401` 검증 실패
- `COMMON-404` 리소스 없음
- `DOC-415` 지원하지 않는 파일 형식
- `DOC-422` 문서 파싱 실패
- `EMB-502` 임베딩 생성 실패
- `LLM-502` LLM 응답 생성 실패
- `AI-429` Gemini quota 초과
- `COMMON-500` 내부 서버 오류

## 3) Chat API

### 3.1 채팅 요청

- Method: `POST`
- URL: `/api/v1/rag/chat`
- Body(`ChatRequest`)
  - `sessionId` (String, optional)
  - `question` (String, required, blank 불가)

설명:
- 첫 턴에서 `sessionId`가 비어 있으면 서버가 새 세션 ID를 생성해서 응답합니다.

응답 data(`ChatResponse`):
- `sessionId`
- `rewrittenQuestion`
- `answer`
- `sources[]`
  - `documentId`, `filename`, `chunkId`, `snippet`, `score`

예시:

```json
{
  "code": "SUCCESS",
  "message": "Request processed successfully.",
  "data": {
    "sessionId": "ses_01JT0Q6Y3GQ4Y8Y2T8F3J7K2H1",
    "rewrittenQuestion": "오독오독 치킨테린 급여 주기 알려줘",
    "answer": "치킨테린은 소형견 기준 하루 1회 권장량을 나눠 급여하세요.",
    "sources": [
      {
        "documentId": "notice_202604",
        "filename": "feeding-guide.pdf",
        "chunkId": "notice_202604-12",
        "snippet": "소형견은 1일 1회, 체중별 급여량을 준수...",
        "score": 0.044
      }
    ]
  },
  "timestamp": "2026-04-28T03:41:22.1054244Z"
}
```

### 3.2 세션 이력 조회

- Method: `GET`
- URL: `/api/v1/rag/sessions/{sessionId}`

응답 data(`SessionHistoryResponse`):
- `sessionId`
- `messageCount`
- `messages[]`
  - `role` (`USER`/`ASSISTANT`)
  - `content`
  - `timestamp` (epoch millis)

## 4) Retrieval API

### 4.1 하이브리드 검색

- Method: `POST`
- URL: `/api/v1/rag/search`
- Body(`HybridSearchRequest`)
  - `question` (String, required, blank 불가)
  - `topK` (Integer, optional)

동작 규칙:
- `topK`가 `null` 또는 `1` 미만이면 서버 기본값(`rag.retrieval.topK`)을 사용합니다.

응답 data(`HybridSearchResponse`):
- `query`
- `topK`
- `results[]`
  - `chunkId`, `documentId`, `filename`, `category`, `chunkIndex`, `text`, `score`

## 5) Document API

### 5.1 문서 업로드 (비동기 파이프라인 시작)

- Method: `POST`
- URL: `/api/v1/rag/documents`
- Content-Type: `multipart/form-data`
- Form Data
  - `file` (required)
  - `document_id` (String, optional)
  - `category` (String, optional)

파일 규격:
- 확장자: `.pdf`, `.docx`, `.txt`
- MIME 타입도 확장자와 일치해야 통과합니다.

`category` 값:
- `FAQ`, `NOTICE`, `POLICY`, 그 외는 `UNKNOWN` 처리

응답 data(`DocumentUploadResponse`):
- `documentId`
- `filename`
- `category`
- `status` (초기값 `pending`)
- `chunkCount` (초기값 `0`)
- `message`

주의:
- 업로드 응답은 즉시 반환되고, 파싱/임베딩/색인은 비동기로 진행됩니다.

### 5.2 문서 상태 조회 (폴링)

- Method: `GET`
- URL: `/api/v1/rag/documents/{documentId}`

응답 data(`DocumentStatusResponse`):
- `documentId`, `filename`, `category`
- `status`
- `errorMessage`
- `chunkCount`
- `createdAt`, `updatedAt`

`status` 값:
- `pending`, `parsing`, `parsed`, `embedding`, `embedded`, `indexing`, `processed`, `failed`

### 5.3 청크 미리보기

- Method: `GET`
- URL: `/api/v1/rag/documents/{documentId}/chunks`

응답 data(`ChunkPreviewResponse`):
- `documentId`
- `chunks[]`
  - `chunkIndex`, `chunkId`, `header`, `tokenCount`, `content`

## 6) 인증/보안 참고

- 게이트웨이 설정 기준 `rag` 경로는 whitelist에 포함되어 있지 않습니다.
- 따라서 배포 환경에서는 게이트웨이 인증 정책(JWT 필터)을 따라 인증이 필요할 수 있습니다.
- 로컬/개발 환경에서의 우회 여부는 배포 설정값에 따라 달라질 수 있습니다.
