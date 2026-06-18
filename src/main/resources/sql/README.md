# 📊 실전 SQL (PostgreSQL 기반)

## 📌 개요
백엔드 개발자에게 필수적인 RDBMS 질의 작성 능력과 실행 계획(EXPLAIN) 최적화 능력을 평가합니다.

## 🎯 연습할 핵심 유형

### [Day 2] SQL 1: 기초 DDL 설계 및 단순 JOIN/집계
- **요구사항**: 
  1. `users`, `orders`, `products` 테이블의 스키마를 설계하고 DDL(Create Table) 작성 (데이터 타입, PK, FK, 제약조건 등 고민)
  2. 유저별 총 주문 건수와 결제 금액을 구하는 쿼리 작성 (`GROUP BY`)
  3. 총 결제 금액이 10만 원 이상인 유저만 필터링 (`HAVING`)
- **목표**: 금액 처리용 데이터 타입 선택, 외래 키(FK)의 올바른 위치, `WHERE`와 `HAVING`의 실행 순서 차이 체득

### [Day 3~5] 고급 SQL
1. **윈도우 함수 (Window Function)**
   - `SUM(amount) OVER (ORDER BY date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)` 등 이동 평균 쿼리 연습
2. **계층형 재귀 쿼리 (Recursive CTE)**
   - `WITH RECURSIVE` 구문을 사용하여 `parent_id`를 기반으로 카테고리 트리(Root -> Child) 전체 경로 출력하기
3. **복잡한 집계 및 서브쿼리**
   - 3개 이상의 다중 테이블 JOIN 및 `GROUP BY`, `HAVING` 활용
   - 최근 30일 이내 구매력이 가장 높은 VIP 유저 및 주 구매 카테고리 추출 등

## 💡 배운점
- 1. 외래 키(FK) 참조 문법: `user_id BIGINT REFERENCES users(id)` 처럼 컬럼 선언부에 `REFERENCES 참조할테이블(참조할컬럼)`을 붙여서 참조 무결성을 보장한다. (이때 컬럼 타입은 대상 PK 타입과 동일해야 함)
- 2. 값의 범위 제약 (CHECK): `stock INTEGER NOT NULL CHECK (stock >= 0)` 처럼 `CHECK (조건식)`을 사용하여 DB 레벨에서 마이너스 재고 같은 비정상적인 데이터 삽입을 원천 차단한다.

## 💡 평가 포인트 (리뷰 기준)
- 작성한 쿼리가 테이블 풀 스캔(Full Scan)을 유발하지 않는가?
- 올바른 복합 인덱스(Composite Index)가 설계되어 인덱스를 타는지 고민했는가?
