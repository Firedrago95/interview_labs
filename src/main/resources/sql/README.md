# 📊 실전 SQL (PostgreSQL 기반)

## 📌 개요
백엔드 개발자에게 필수적인 RDBMS 질의 작성 능력과 실행 계획(EXPLAIN) 최적화 능력을 평가합니다.

## 🎯 연습할 핵심 유형
1. **윈도우 함수 (Window Function)**
   - `SUM(amount) OVER (ORDER BY date ROWS BETWEEN 6 PRECEDING AND CURRENT ROW)` 등 이동 평균 쿼리 연습
2. **계층형 재귀 쿼리 (Recursive CTE)**
   - `WITH RECURSIVE` 구문을 사용하여 `parent_id`를 기반으로 카테고리 트리(Root -> Child) 전체 경로 출력하기
3. **복잡한 집계 및 서브쿼리**
   - 3개 이상의 다중 테이블 JOIN 및 `GROUP BY`, `HAVING` 활용
   - 최근 30일 이내 구매력이 가장 높은 VIP 유저 및 주 구매 카테고리 추출 등

## 💡 평가 포인트 (리뷰 기준)
- 작성한 쿼리가 테이블 풀 스캔(Full Scan)을 유발하지 않는가?
- 올바른 복합 인덱스(Composite Index)가 설계되어 인덱스를 타는지 고민했는가?
