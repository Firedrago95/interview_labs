# [VMS 과제] S2. 선원 익명 커뮤니티 게시판 - 실행 계획 (spec.md)

# [VMS 과제] S2. 선원 익명 커뮤니티 게시판 - 실행 계획 (spec.md)

## 1. 데이터 모델 (ERD 및 테이블 정의)

물리적 FK 제약조건은 배제하였으며, 조회를 위한 인덱스만 구성합니다.

### 1.1 `users` (사용자)

* `id` (VARCHAR, PK)
* `name` (VARCHAR, Not Null)

### 1.2 `posts` (게시글)

* `id` (BIGINT, PK, Auto Increment)
* `channel_type` (VARCHAR, Not Null) - 'REAL_NAME' | 'ANONYMOUS'
* `author_id` (VARCHAR, Not Null) - 논리적 외래키 to users.id
* `title` (VARCHAR, Not Null)
* `content` (TEXT, Not Null)
* `view_count` (BIGINT, Default 0)
* `like_count` (BIGINT, Default 0)
* `dislike_count` (BIGINT, Default 0)
* `comment_count` (BIGINT, Default 0)
* `is_deleted` (BOOLEAN, Default false)
* `created_at` (TIMESTAMP, Not Null, Default CURRENT_TIMESTAMP)
* **Indexes:** `idx_channel_id` (channel_type, id DESC) - 페이지네이션용

### 1.3 `post_nickname_mappings` (익명 닉네임 매핑)

* `id` (BIGINT, PK, Auto Increment)
* `post_id` (BIGINT, Not Null) - 논리적 외래키 to posts.id
* `user_id` (VARCHAR, Not Null) - 논리적 외래키 to users.id
* `nickname` (VARCHAR, Not Null) - 예: "푸른 고래"
* `is_author` (BOOLEAN, Default false)
* **Constraints/Indexes:**
    * `uk_post_user` UNIQUE (post_id, user_id) : 1인 1닉네임 보장
    * `uk_post_nickname` UNIQUE (post_id, nickname) : 닉네임 충돌 방지

### 1.4 `comments` (댓글)

* `id` (BIGINT, PK, Auto Increment)
* `post_id` (BIGINT, Not Null) - 논리적 외래키 to posts.id
* `user_id` (VARCHAR, Not Null) - 논리적 외래키 to users.id
* `parent_id` (BIGINT, Nullable) - 논리적 외래키 to comments.id (최대 1단계 깊이)
* `content` (VARCHAR, Not Null)
* `like_count` (BIGINT, Default 0)
* `dislike_count` (BIGINT, Default 0)
* `is_deleted` (BOOLEAN, Default false)
* `created_at` (TIMESTAMP, Not Null, Default CURRENT_TIMESTAMP)
* **Indexes:** `idx_post_parent_created` (post_id, parent_id, created_at)

### 1.5 `reactions` (반응)

* `id` (BIGINT, PK, Auto Increment)
* `target_type` (VARCHAR, Not Null) - 'POST' | 'COMMENT'
* `target_id` (BIGINT, Not Null)
* `user_id` (VARCHAR, Not Null)
* `reaction_type` (VARCHAR, Nullable) - 'LIKE' | 'DISLIKE' | Null(취소)
* **Constraints/Indexes:**
    * `uk_target_user` UNIQUE (target_type, target_id, user_id)

---

## 2. API 설계

모든 API는 인증을 위해 요청 헤더에 `X-User-Id: {userId}`를 포함한다고 가정합니다

### 2.1 게시글 도메인 (Posts)

#### 1) 게시글 작성

- **Endpoint:** `POST /api/v1/posts`
- **Description:** 새로운 게시글을 작성합니다. 채널 타입에 따라 실명 혹은 익명으로 처리됩니다. **익명 게시글의 경우, 작성자의 닉네임
  매핑(`is_author=true`)을 트랜잭션 내에서 즉시 생성하여 상세 조회 시 닉네임 노출을 보장합니다.**
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
- **Description:** 채널별 게시글 목록을 페이지네이션하여 조회합니다. 처음 조회 시에는 `lastPostId`를 비웁니다. `is_deleted = true`인
  게시글은 목록 조회 대상에서 제외됩니다.
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
- **Description:** 게시글 상세 내용을 조회합니다. 원자적 업데이트(`UPDATE ... SET view_count = view_count + 1`)를 통해 조회수를
  1 증가시키며, 삭제된 게시글인 경우 조회가 불가합니다.
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
- **Description:** 게시글에 댓글 또는 답글을 작성합니다. 답글의 답글은 허용되지 않습니다. 댓글 저장 전 `resolveNickname`을 호출하여 작성자 닉네임을
  생성/조회하며, 성공 시 게시글의 `comment_count`를 원자적으로 1 증가시킵니다.
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
- **Description:** 댓글을 삭제합니다. 답글이 있는 경우 내용만 대체됩니다. 삭제 성공 시 게시글의 `comment_count`를 원자적으로 1 감소시킵니다.
- **Response (204 No Content)**

## 2.3 반응 도메인 (Reactions)

### 1) 좋아요/싫어요 토글

- **Endpoint:** `POST /api/v1/reactions`
- **Description:** 게시글 또는 댓글에 좋아요/싫어요 반응을 남기거나 취소/변경합니다. `targetType`에 따라 대상(게시글 혹은 댓글)의
  `like_count`, `dislike_count`를 상태 전이 규칙에 맞춰 원자적으로 갱신하여 정합성을 보장합니다.
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
// 게시글 생성(isAuthor=true) 및 댓글 작성(isAuthor=false) 시 호출
String resolveNickname(Long postId, String userId, boolean isAuthor) { // 파라미터 추가
    // 1. 이미 매핑된 닉네임이 있는지 조회
    String existingNickname = mappingRepository.findNickname(postId, userId);
    if (existingNickname != null)
        return existingNickname;

    // 2. 닉네임 생성 및 충돌 제어 (최대 3회 재시도)
    for (int attempt = 1; attempt <= 4; attempt++) {
        try {
            String newNickname = generateRandomNickname();
            if (attempt == 4) {
                newNickname += "_" + generateRandomNumber(100, 999);
            }
            // is_author 플래그를 함께 저장하여 게시글 작성자 여부 기록
            mappingRepository.save(postId, userId, newNickname, isAuthor);
            return newNickname;
        } catch (DataIntegrityViolationException e) {
            continue;
        }
    }
    throw new ServerException("닉네임 할당 실패");
}
```

### 3.2 반응 전이 규칙 및 카운트 동기화

* reactions 테이블과 posts.like_count 간의 정합성을 맞추기 위한 상태 전이.

```java
@Transactional
void toggleReaction(String targetType, Long targetId, String userId, ReactionType newReaction) {
    // 1. 타겟 레코드 유효성 검사 (존재 여부 & soft-delete 상태)
    validateTargetExistsAndNotDeleted(targetType, targetId);

    // 2. 기존 반응 상태 조회 (targetType 포함)
    ReactionType oldReaction = reactionRepository.findReactionType(targetType, targetId, userId);

    // 3. 상태 전이 로직 (State Matrix - 이전과 동일)
    ReactionType finalReaction;
    int likeDelta = 0, dislikeDelta = 0;
    // ... (중략: 기존 likeDelta/dislikeDelta 계산 로직) ...

    // 4. DB 반영 (UPSERT 및 카운트 원자적 갱신) 
    reactionRepository.upsert(targetType, targetId, userId, finalReaction);

    // 타겟 타입에 따른 분기 처리 추가
    if ("POST".equals(targetType)) {
        postRepository.updateReactionCounts(targetId, likeDelta, dislikeDelta);
    } else {
        commentRepository.updateReactionCounts(targetId, likeDelta, dislikeDelta);
    }
}
```

---

## 4. Edge Case 분석 및 처리 전략

### 4.1 논리적 외래키(FK)의 참조 무결성 및 동시성 방어

- **상황:** 물리적 FK가 없기 때문에 삭제된 게시글에 댓글/좋아요가 생성될 위험이 있으며, 단순 조회 검증만으로는 찰나의 순간에 게시글이 삭제되는 '
  Check-Then-Act' 레이스 컨디션을 막을 수 없음.
- **처리:**
    1. **ORM 레벨:** 엔티티에 `@SQLRestriction("is_deleted = false")` (또는 `@Where`)를 적용하여 소프트 딜리트된 데이터의
       조회를 애플리케이션 전역에서 1차 차단함.
    2. **비즈니스 레벨:** 영속성 컨텍스트의 지연 데이터 문제를 방지하기 위해, Write(POST/DELETE) 하기 전에 db에 재조회 하여 명시적으로 확인합니다.
    3. **DB 쿼리 레벨 (극단적 동시성 고려):** 초고트래픽 상황에서 완벽한 정합성이 요구되는 경우, 단순 `INSERT` 대신
       `INSERT INTO ... SELECT ... WHERE is_deleted = false` 형태의 원자적 쿼리를 활용해 DB 락 없이 고아 데이터 생성을
       물리적으로 차단함.

### 4.2 삭제된 부모 댓글의 노출 문제

- **상황:** 부모 댓글을 물리적 삭제하면 자식 답글이 고아가 됨
- **처리:** `is_deleted = true`로 Soft Delete 처리  
  API 응답 시 `is_deleted`가 true이면 `content` 필드를 "삭제된 댓글입니다"로 매핑하여 전달

### 4.3 과도한 조회수 증가 방지

- **상황:** 새로고침 연타로 인한 무의미한 조회수 어뷰징
- **처리:** 일차적으로 서버내 로컬 캐시에서 (userId, postId) 키를 1시간 만료로 저장해 어뷰징을 막고, 단일 인스턴스 한계를 넘어서는 확장이 필요할때,
  Redis 기반 분산 캐시로 전환함.

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
