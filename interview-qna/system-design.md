# 🏗️ System Design 면접 기출 및 핵심 이론

> **📝 작성 가이드 (아래 템플릿을 복사해서 새 질문을 추가하세요)**
> ```markdown
> <details>
> <summary><b>Q. 질문 제목을 적어주세요.</b></summary>
> <div markdown="1">
> 
> - **핵심 키워드**: 대답에 반드시 포함되어야 할 키워드
> - **나의 답변**: 면접에서 실제로 말할 스크립트 형태의 답변
> - **💡 꼬리질문**:
>   - *Q. 꼬리질문 내용?*
>     - A. 꼬리질문 답변 내용
> </div>
> </details>
> ```

---

<details>
<summary><b>Q. 단일 DB에서 처리할 수 없는 대규모 트래픽이 발생했을 때 해결할 수 있는 방안들을 설명해주세요.</b></summary>
<div markdown="1">

- **핵심 키워드**: Scale-up, Scale-out, Replication(Read/Write 분리), Sharding, Caching
- **나의 답변**: 먼저 Redis와 같은 캐시 계층을 두어 DB 부하를 줄일 수 있습니다. 또한 Replication을 통해 Master-Slave 구조로 Read와 Write 연산을 분리하거나... (상세 내용 작성...)
- **💡 꼬리질문**:
  - *Q. Replication 적용 시 데이터 동기화 지연(Replication Lag)이 발생하면 어떻게 대처하나요?*
    - A. 강한 일관성이 필요한 데이터(예: 결제 내역)는 Master에서 직접 읽어오도록 라우팅하고, 그 외에는 약간의 지연을 허용하는 최종 일관성(Eventual Consistency) 모델을 적용합니다.
</div>
</details>
