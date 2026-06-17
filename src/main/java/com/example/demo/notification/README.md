# 🔔 알림 시스템 (Fan-out 처리)

## 📌 개요
데이터 변경 이벤트를 감지하고 이를 다수의 대상에게 효율적으로 전파(Propagation)하는 아키텍처 능력을 평가합니다.

## 🎯 요구사항
1. **게시글 작성 알림**
   - 특정 유저가 게시글을 작성하면, 해당 유저를 '팔로우'하고 있는 모든 유저에게 새 글 알림을 생성합니다.
2. **대량 발송 처리**
   - 만약 글 작성자가 10만 명의 팔로워를 가진 인플루언서라면, 알림 생성 로직이 본 트랜잭션을 지연시키면 안 됩니다.

## 💡 평가 포인트 (리뷰 기준)
- Spring의 ApplicationEventPublisher나 `@TransactionalEventListener`를 사용하여 도메인 로직과 알림 로직을 분리(Decoupling)했는가?
- 대량의 알림 발송을 비동기(Async) 큐나 Kafka로 밀어 넣어 백그라운드 워커가 처리하도록 구성했는가? (Fan-out on Write vs Fan-out on Read 고민)
