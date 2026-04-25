# [VMS 과제] S2. 선원 익명 커뮤니티 게시판 - 실행 계획 (spec.md)

## 1. 데이터 모델 (ERD 및 테이블 정의)
물리적 FK 제약조건은 배제하였으며, 조회를 위한 인덱스만 구성합니다.

### 1.1 `users` (사용자 - 가정)
* `id` (VARCHAR, PK)
* `real_name` (VARCHAR, Not Null)

### 1.2 `posts` (게시글)
* `id` (BIGINT, PK, Auto Increment)
* `channel_type` (VARCHAR, Not Null) - 'REAL_NAME' | 'ANONYMOUS'
* `author_id` (VARCHAR, Not Null) - Logical FK to users.id
* `title` (VARCHAR, Not Null)
* `content` (TEXT, Not Null)
* `view_count` (BIGINT, Default 0)
* `like_count` (BIGINT, Default 0)
* `comment_count` (BIGINT, Default 0)
* `is_deleted` (BOOLEAN, Default false)
* `created_at` (TIMESTAMP)
* **Indexes:** `idx_channel_created` (channel_type, created_at DESC) - 페이지네이션용

### 1.3 `post_nickname_mappings` (익명 닉네임 매핑)
* `id` (BIGINT, PK, Auto Increment)
* `post_id` (BIGINT, Not Null) - Logical FK to posts.id
* `user_id` (VARCHAR, Not Null) - Logical FK to users.id
* `nickname` (VARCHAR, Not Null) - 예: "푸른 고래"
* `is_author` (BOOLEAN, Default false)
* **Constraints/Indexes:**
    * `uk_post_user` UNIQUE (post_id, user_id) : 1인 1닉네임 보장
    * `uk_post_nickname` UNIQUE (post_id, nickname) : 닉네임 충돌 방지

### 1.4 `comments` (댓글)
* `id` (BIGINT, PK, Auto Increment)
* `post_id` (BIGINT, Not Null) - Logical FK to posts.id
* `user_id` (VARCHAR, Not Null) - Logical FK to users.id
* `parent_id` (BIGINT, Nullable) - Logical FK to comments.id (최대 1단계 깊이)
* `content` (VARCHAR, Not Null)
* `is_deleted` (BOOLEAN, Default false) - Soft Delete 상태
* **Indexes:** `idx_post_parent_created` (post_id, parent_id, created_at)

### 1.5 `reactions` (반응)
* `id` (BIGINT, PK, Auto Increment)
* `target_type` (VARCHAR, Not Null) - 'POST' | 'COMMENT'
* `target_id` (BIGINT, Not Null)
* `user_id` (VARCHAR, Not Null)
* `reaction_type` (VARCHAR, Nullable) - 'LIKE' | 'DISLIKE' | Null(취소상태)
* **Constraints/Indexes:**
    * `uk_target_user` UNIQUE (target_type, target_id, user_id)

---

## 2. API 설계
모든 API는 인증을 위해 요청 헤더에 `X-User-Id: {userId}`를 포함한다고 가정합니다
### 2.1 게시글 도메인 (Posts)

#### 1) 게시글 작성
- **Endpoint:** `POST /api/v1/posts`
- **Description:** 새로운 게시글을 작성합니다. 채널 타입에 따라 실명 혹은 익명으로 처리됩니다.
- **Request Body:**
  ```json
  {
    "channelType": "ANONYMOUS", // 'REAL_NAME' 또는 'ANONYMOUS'
    "title": "항해 중 엔진 점검 팁 공유합니다",
    "content": "최근 겪었던 고온 경보 해결 사례입니다..."
  }
  ```
- **Response (201 Created):** `{"postId": 100}`

#### 2) 게시글 목록 조회 (커서 기반 페이지네이션)
- **Endpoint:** `GET /api/v1/posts`
- **Description:** 마지막 조회 ID(`lastPostId`)를 기준으로 채널별 목록을 조회합니다. 처음 조회 시에는 `lastPostId`를 비우거나 최대값을 보냅니다.
- **Query Parameters:**
  - `channelType`: (Required) 'REAL_NAME' | 'ANONYMOUS'
  - `lastPostId`: (Optional) 지난 페이지의 마지막 게시글 ID (Long)
  - `size`: (Optional) 조회할 항목 수 (Default: 20)
- **Response (200 OK):**
  ```json
  {
  "content": [
    {
      "postId": 100,
      "title": "항해 중 엔진 점검 팁...",
      "viewCount": 42,
      "likeCount": 15,
      "commentCount": 3,
      "createdAt": "2026-04-25T10:00:00Z"
    }
  ],
  "lastPostId": 81, // 다음 조회를 위한 커서값 (content의 마지막 ID)
  "hasNext": true   // 다음 페이지 존재 여부
  }
  ```
#### 3) 게시글 상세 조회
- **Endpoint:** `GET /api/v1/posts/{postId}`
- **Description:** 게시글 상세 내용을 조회하며, 조회수가 1증가합니다.
- **Response (200 OK):**
  ```json
  {
  "postId": 100,
  "channelType": "ANONYMOUS",
  "title": "항해 중 엔진 점검 팁...",
  "content": "상세 내용 전문...",
  "authorDisplayName": "용감한 고래", // 채널 타입에 따라 실명 또는 익명 닉네임 반환
  "viewCount": 43,
  "likeCount": 15,
  "createdAt": "2026-04-25T10:00:00Z"
  }
  ```

#### 4) 게시글 수정/삭제
- **수정:** `PATCH /api/v1/posts/{postId}` (Request Body에 수정할 title, content 포함)
- **삭제:** `DELETE /api/v1/posts/{postId}` (Soft Delete 처리)


### 2.2 댓글 도메인 (Comments)
#### 1) 댓글 및 답글 작성
- **Endpoint:** `POST /api/v1/posts/{postId}/comments`
- **Description:** 게시글에 댓글 또는 답글을 작성합니다. 답글의 답글은 허용되지 않습니다.
- **Request Body:**
  ```json
  {
  "parentId": null, // 답글일 경우 부모 댓글의 ID 입력
  "content": "좋은 정보 감사합니다!"
  }
  ```
- **Response (201 Created):** 
  ```json
  { 
  "commentId": 20, 
  "displayName": "조용한 돌고래",
  "content": "좋은 정보 감사합니다!",
  "createdAt": "2026-04-25T10:05:00Z"
  }
  ```

#### 2) 댓글 삭제
- **Endpoint:** `DELETE /api/v1/posts/{postId}/comments/{commentId}`
- **Description:** 댓글을 삭제합니다. 답글이 있는 경우 내용만 대체됩니다.
- **Response (204 No Content)**

## 2.3 반응 도메인 (Reactions)

### 1) 좋아요/싫어요 토글
- **Endpoint:** `POST /api/v1/reactions`
- **Description:** 게시글 또는 댓글에 좋아요/싫어요 반응을 남기거나 취소/변경합니다.
- **Request Body:**
  ```json
  {
  "targetType": "POST", // 'POST' 또는 'COMMENT'
  "targetId": 100,
  "reactionType": "LIKE" // 'LIKE' 또는 'DISLIKE'
  }
  ```
- **Response (200 OK):**
  ```json
  {
  "currentReaction": "LIKE", // 다시 눌러 취소된 경우 null
  "targetLikeCount": 16,
  "targetDislikeCount": 0
  }
  ```
---

## 3. 핵심 비즈니스 로직 (Pseudo-code)

### 3.1 익명 닉네임 생성 및 할당 로직
```java
// 사용자가 댓글 작성 시 호출
String resolveNickname(Long postId, String userId) {
    // 1. 이미 매핑된 닉네임이 있는지 조회
    String existingNickname = mappingRepository.findNickname(postId, userId);
    if (existingNickname != null) return existingNickname;

    // 2. 닉네임 생성 및 충돌 제어 (최대 3회 재시도)
    for (int attempt = 1; attempt <= 4; attempt++) {
        try {
            String newNickname = generateRandomNickname(); // 형용사 + 해양생물
            if (attempt == 4) {
                newNickname += "_" + generateRandomNumber(100, 999); // Fallback
            }
            mappingRepository.save(postId, userId, newNickname);
            return newNickname;
        } catch (DataIntegrityViolationException e) {
            // UNIQUE 제약조건 위배 시 예외 무시하고 다음 루프 진행
            continue; 
        }
    }
    throw new ServerException("닉네임 할당 실패");
}
```
---

## 4. Edge Case 분석 및 처리 전략

### 4.1 삭제된 부모 댓글의 노출 문제
- **상황:** 부모 댓글을 물리적 삭제하면 자식 답글이 고아가 됨
- **처리:** `is_deleted = true`로 Soft Delete 처리  
  API 응답 시 `is_deleted`가 true이면 `content` 필드를 "삭제된 댓글입니다"로 매핑하여 전달

### 4.2 무의미한 조회수 어뷰징
- **상황:** 한 사용자가 새로고침을 반복하여 게시글 조회수가 급증함
- **처리:** `X-User-Id` 기준으로 Redis 또는 로컬 캐시(만료 1시간)에 조회 이력을 기록하여  
  중복 조회수 증가를 방지함

---

## 5. 핵심 테스트 시나리오

### 5.1 동시성 닉네임 발급 충돌 방지
- **Given:** 익명 게시글 1번이 생성되어 있다
- **When:** User A와 User B가 동일한 시점에 첫 댓글 작성을 시도하며,  
  서버 로직이 두 사용자 모두에게 "용감한 고래"라는 동일한 닉네임을 생성했다
- **Then:** 데이터베이스의 `uk_post_nickname` 유니크 제약조건에 의해  
  하나의 트랜잭션만 INSERT에 성공하고, 다른 트랜잭션은 예외를 감지하여  
  "차가운 돌고래"로 재시도(Retry)하여 저장한다

### 5.2 삭제된 댓글의 답글 처리
- **Given:** User A의 댓글(ID:1)에 User B의 답글(ID:2)이 달려 있다
- **When:** User A가 본인의 댓글(ID:1)을 삭제한다
- **Then:** 댓글 1의 `is_deleted`는 true가 된다  
  게시글 조회 API 호출 시 댓글 1의 내용은 "삭제된 댓글입니다"로 노출되며,  
  하위 답글(ID:2)은 정상적으로 노출된다
